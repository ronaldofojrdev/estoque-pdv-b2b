package dev.ronaldofojr.pdv.service;

import dev.ronaldofojr.pdv.domain.Customer;
import dev.ronaldofojr.pdv.domain.MovementType;
import dev.ronaldofojr.pdv.domain.PaymentMethod;
import dev.ronaldofojr.pdv.domain.Product;
import dev.ronaldofojr.pdv.domain.Sale;
import dev.ronaldofojr.pdv.domain.SaleItem;
import dev.ronaldofojr.pdv.domain.StockMovement;
import dev.ronaldofojr.pdv.repository.CustomerRepository;
import dev.ronaldofojr.pdv.repository.ProductRepository;
import dev.ronaldofojr.pdv.repository.SaleItemRepository;
import dev.ronaldofojr.pdv.repository.SaleRepository;
import dev.ronaldofojr.pdv.repository.StockMovementRepository;
import dev.ronaldofojr.pdv.service.Exceptions.BusinessRuleException;
import dev.ronaldofojr.pdv.service.Exceptions.InsufficientStockException;
import dev.ronaldofojr.pdv.service.Exceptions.NotFoundException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SaleService {

    private static final Logger log = LoggerFactory.getLogger(SaleService.class);

    public record ItemRequest(UUID productId, int quantity) {
    }

    public record SaleDetail(Sale sale, List<SaleItem> items) {
    }

    public record CreationResult(SaleDetail detail, boolean created) {
    }

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final StockMovementRepository movements;
    private final TransactionTemplate tx;

    public SaleService(SaleRepository sales, SaleItemRepository saleItems, ProductRepository products,
                       CustomerRepository customers, StockMovementRepository movements, TransactionTemplate tx) {
        this.sales = sales;
        this.saleItems = saleItems;
        this.products = products;
        this.customers = customers;
        this.movements = movements;
        this.tx = tx;
    }

    /**
     * Registra a venda e baixa o estoque, tudo na mesma transacao: ou a venda inteira vale ou nada muda.
     *
     * clientSaleId e gerado pelo cliente (um UUID por venda). Se a mesma venda chegar duas vezes (rede caiu na resposta,
     * cliente reenviou), a segunda devolve a venda ja registrada em vez de baixar o estoque de novo.
     */
    public CreationResult create(UUID clientSaleId, UUID customerId, PaymentMethod paymentMethod,
                                 List<ItemRequest> requested, String user) {
        try {
            return tx.execute(status -> createInTransaction(clientSaleId, customerId, paymentMethod, requested, user));
        } catch (DataIntegrityViolationException e) {
            // duas requisicoes com o mesmo clientSaleId ao mesmo tempo: a unique do banco barrou a segunda
            return tx.execute(status -> sales.findByClientSaleId(clientSaleId)
                    .map(existing -> new CreationResult(detailOf(existing), false))
                    .orElseThrow(() -> e));
        }
    }

    private CreationResult createInTransaction(UUID clientSaleId, UUID customerId, PaymentMethod paymentMethod,
                                               List<ItemRequest> requested, String user) {
        var existing = sales.findByClientSaleId(clientSaleId);
        if (existing.isPresent()) {
            log.info("Venda {} ja registrada, devolvendo a existente", clientSaleId);
            return new CreationResult(detailOf(existing.get()), false);
        }

        Customer customer = customers.findById(customerId).orElseThrow(() -> new NotFoundException("Cliente", customerId));
        if (!customer.isActive()) {
            throw new BusinessRuleException("Cliente inativo");
        }

        // junta linhas repetidas do mesmo produto e ordena por id: todas as vendas travam as linhas
        // na mesma ordem, o que evita deadlock entre vendas simultaneas com itens em comum
        Map<UUID, Integer> quantities = new TreeMap<>();
        for (ItemRequest item : requested) {
            if (item.quantity() <= 0) {
                throw new BusinessRuleException("A quantidade de cada item deve ser maior que zero");
            }
            quantities.merge(item.productId(), item.quantity(), Integer::sum);
        }
        if (quantities.isEmpty()) {
            throw new BusinessRuleException("A venda precisa ter ao menos um item");
        }

        Map<UUID, Product> found = new java.util.HashMap<>();
        products.findAllByIdIn(quantities.keySet()).forEach(p -> found.put(p.getId(), p));

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            Product product = found.get(entry.getKey());
            if (product == null) {
                throw new NotFoundException("Produto", entry.getKey());
            }
            if (!product.isActive()) {
                throw new BusinessRuleException("O produto " + product.getSku() + " esta inativo");
            }
            total = total.add(product.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        // baixa atomica: UPDATE ... WHERE quantity >= :qty. Se algum item falhar, a excecao desfaz as baixas dos anteriores.
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            if (products.decrementStock(entry.getKey(), entry.getValue()) == 0) {
                Product product = found.get(entry.getKey());
                Integer available = products.currentQuantity(entry.getKey());
                throw new InsufficientStockException(product.getId(), product.getSku(),
                        available == null ? 0 : available, entry.getValue());
            }
        }

        Sale sale = sales.saveAndFlush(new Sale(clientSaleId, customerId, paymentMethod, total, user));
        List<SaleItem> items = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : quantities.entrySet()) {
            items.add(new SaleItem(sale.getId(), found.get(entry.getKey()), entry.getValue()));
            movements.save(new StockMovement(entry.getKey(), MovementType.SALE, -entry.getValue(),
                    null, sale.getId(), user));
        }
        saleItems.saveAll(items);
        return new CreationResult(new SaleDetail(sale, items), true);
    }

    /** Cancela a venda e devolve os itens ao estoque. Cancelar duas vezes nao devolve duas vezes. */
    @Transactional
    public SaleDetail cancel(UUID saleId, String user) {
        // lock pessimista: dois cancelamentos simultaneos da mesma venda ficam em fila e o segundo ve CANCELED
        Sale sale = sales.findForUpdate(saleId).orElseThrow(() -> new NotFoundException("Venda", saleId));
        List<SaleItem> items = saleItems.findBySaleIdOrderByProductName(saleId);
        if (sale.cancel()) {
            for (SaleItem item : items) {
                products.incrementStock(item.getProductId(), item.getQuantity());
                movements.save(new StockMovement(item.getProductId(), MovementType.SALE_CANCEL, item.getQuantity(),
                        "Cancelamento da venda", saleId, user));
            }
            sales.save(sale);
        }
        return new SaleDetail(sale, items);
    }

    @Transactional(readOnly = true)
    public SaleDetail get(UUID id) {
        Sale sale = sales.findById(id).orElseThrow(() -> new NotFoundException("Venda", id));
        return detailOf(sale);
    }

    @Transactional(readOnly = true)
    public Page<Sale> list(Pageable pageable) {
        return sales.findAllByOrderByCreatedAtDesc(pageable);
    }

    private SaleDetail detailOf(Sale sale) {
        return new SaleDetail(sale, saleItems.findBySaleIdOrderByProductName(sale.getId()));
    }
}
