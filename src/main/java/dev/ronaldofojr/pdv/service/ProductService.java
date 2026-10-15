package dev.ronaldofojr.pdv.service;

import dev.ronaldofojr.pdv.domain.MovementType;
import dev.ronaldofojr.pdv.domain.Product;
import dev.ronaldofojr.pdv.domain.StockMovement;
import dev.ronaldofojr.pdv.repository.ProductRepository;
import dev.ronaldofojr.pdv.repository.StockMovementRepository;
import dev.ronaldofojr.pdv.service.Exceptions.BusinessRuleException;
import dev.ronaldofojr.pdv.service.Exceptions.ConflictException;
import dev.ronaldofojr.pdv.service.Exceptions.InsufficientStockException;
import dev.ronaldofojr.pdv.service.Exceptions.NotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private final ProductRepository products;
    private final StockMovementRepository movements;

    public ProductService(ProductRepository products, StockMovementRepository movements) {
        this.products = products;
        this.movements = movements;
    }

    @Transactional
    public Product create(String sku, String name, BigDecimal price, int initialQuantity, int minStock, String user) {
        String normalizedSku = sku.trim().toUpperCase();
        if (products.existsBySku(normalizedSku)) {
            throw new ConflictException("Ja existe um produto com o SKU " + normalizedSku);
        }
        try {
            Product product = products.saveAndFlush(new Product(normalizedSku, name, price, initialQuantity, minStock));
            if (initialQuantity > 0) {
                movements.save(new StockMovement(product.getId(), MovementType.IN, initialQuantity,
                        "Estoque inicial", null, user));
            }
            return product;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Ja existe um produto com o SKU " + normalizedSku);
        }
    }

    @Transactional
    public Product update(UUID id, String name, BigDecimal price, int minStock, boolean active) {
        Product product = get(id);
        product.update(name, price, minStock, active);
        return products.save(product);
    }

    @Transactional(readOnly = true)
    public Product get(UUID id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Produto", id));
    }

    @Transactional(readOnly = true)
    public Page<Product> search(String q, boolean onlyActive, Pageable pageable) {
        return products.search(q == null ? "" : q.trim(), onlyActive, pageable);
    }

    @Transactional(readOnly = true)
    public List<Product> lowStock() {
        return products.findLowStock();
    }

    /** Entrada ou saida manual de estoque. A saida nunca deixa o saldo negativo. */
    @Transactional
    public Product adjust(UUID productId, MovementType type, int quantity, String reason, String user) {
        if (type != MovementType.IN && type != MovementType.OUT) {
            throw new BusinessRuleException("Ajuste manual so aceita IN ou OUT");
        }
        if (quantity <= 0) {
            throw new BusinessRuleException("A quantidade deve ser maior que zero");
        }
        Product product = get(productId);

        if (type == MovementType.IN) {
            products.incrementStock(productId, quantity);
        } else if (products.decrementStock(productId, quantity) == 0) {
            Integer available = products.currentQuantity(productId);
            throw new InsufficientStockException(productId, product.getSku(), available == null ? 0 : available, quantity);
        }
        int delta = type == MovementType.IN ? quantity : -quantity;
        movements.save(new StockMovement(productId, type, delta, reason, null, user));
        return get(productId);
    }

    @Transactional(readOnly = true)
    public List<StockMovement> movementsOf(UUID productId) {
        get(productId);
        return movements.findTop100ByProductIdOrderByCreatedAtDesc(productId);
    }
}
