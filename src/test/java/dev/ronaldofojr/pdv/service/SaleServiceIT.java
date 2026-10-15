package dev.ronaldofojr.pdv.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ronaldofojr.pdv.IntegrationTestBase;
import dev.ronaldofojr.pdv.domain.Customer;
import dev.ronaldofojr.pdv.domain.PaymentMethod;
import dev.ronaldofojr.pdv.domain.Product;
import dev.ronaldofojr.pdv.domain.SaleStatus;
import dev.ronaldofojr.pdv.service.Exceptions.BusinessRuleException;
import dev.ronaldofojr.pdv.service.Exceptions.InsufficientStockException;
import dev.ronaldofojr.pdv.service.SaleService.CreationResult;
import dev.ronaldofojr.pdv.service.SaleService.ItemRequest;
import dev.ronaldofojr.pdv.service.SaleService.SaleDetail;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SaleServiceIT extends IntegrationTestBase {

    @Autowired
    SaleService saleService;

    private CreationResult sell(UUID clientSaleId, Customer customer, Object... productAndQuantity) {
        List<ItemRequest> items = new ArrayList<>();
        for (int i = 0; i < productAndQuantity.length; i += 2) {
            items.add(new ItemRequest(((Product) productAndQuantity[i]).getId(), (Integer) productAndQuantity[i + 1]));
        }
        return saleService.create(clientSaleId, customer.getId(), PaymentMethod.PIX, items, "vendedor");
    }

    @Test
    void saleDecrementsStockAndComputesTotal() {
        Product bolt = newProduct("PAR-1", "2.50", 100, 10);
        Product nut = newProduct("POR-1", "1.20", 50, 10);
        Customer customer = newCustomer();

        CreationResult result = sell(UUID.randomUUID(), customer, bolt, 10, nut, 5);

        assertThat(result.created()).isTrue();
        assertThat(result.detail().sale().getTotal()).isEqualByComparingTo("31.00"); // 10*2.50 + 5*1.20
        assertThat(result.detail().items()).hasSize(2);
        assertThat(stockOf(bolt)).isEqualTo(90);
        assertThat(stockOf(nut)).isEqualTo(45);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE type = 'SALE'", Integer.class)).isEqualTo(2);
    }

    @Test
    void repeatedLinesOfTheSameProductAreMerged() {
        Product bolt = newProduct("PAR-2", "2.00", 20, 0);

        CreationResult result = sell(UUID.randomUUID(), newCustomer(), bolt, 3, bolt, 4);

        assertThat(result.detail().items()).hasSize(1);
        assertThat(result.detail().items().get(0).getQuantity()).isEqualTo(7);
        assertThat(stockOf(bolt)).isEqualTo(13);
    }

    @Test
    void sameClientSaleIdTwiceCreatesOneSaleAndDecrementsOnce() {
        Product bolt = newProduct("PAR-3", "5.00", 10, 0);
        Customer customer = newCustomer();
        UUID clientSaleId = UUID.randomUUID();

        CreationResult first = sell(clientSaleId, customer, bolt, 4);
        CreationResult second = sell(clientSaleId, customer, bolt, 4);

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.detail().sale().getId()).isEqualTo(first.detail().sale().getId());
        assertThat(stockOf(bolt)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales", Integer.class)).isEqualTo(1);
    }

    @Test
    void sameClientSaleIdSentAtTheSameTimeDecrementsOnce() throws Exception {
        Product bolt = newProduct("PAR-4", "5.00", 100, 0);
        Customer customer = newCustomer();
        UUID clientSaleId = UUID.randomUUID();

        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<CreationResult>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> sell(clientSaleId, customer, bolt, 10)));
            }
            long created = 0;
            for (Future<CreationResult> f : futures) {
                if (f.get().created()) {
                    created++;
                }
            }
            assertThat(created).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        assertThat(stockOf(bolt)).isEqualTo(90);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales", Integer.class)).isEqualTo(1);
    }

    @Test
    void insufficientStockRejectsTheWholeSaleAndChangesNothing() {
        Product plenty = newProduct("OK-1", "1.00", 100, 0);
        Product scarce = newProduct("POUCO-1", "1.00", 2, 0);
        Customer customer = newCustomer();

        assertThatThrownBy(() -> sell(UUID.randomUUID(), customer, plenty, 10, scarce, 3))
                .isInstanceOfSatisfying(InsufficientStockException.class, e -> {
                    assertThat(e.getAvailable()).isEqualTo(2);
                    assertThat(e.getProductId()).isEqualTo(scarce.getId());
                });

        // a baixa do primeiro item foi desfeita junto com a venda
        assertThat(stockOf(plenty)).isEqualTo(100);
        assertThat(stockOf(scarce)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE type = 'SALE'", Integer.class)).isZero();
    }

    @Test
    void concurrentSalesNeverOversell() throws Exception {
        Product last = newProduct("ULT-1", "10.00", 5, 0);
        Customer customer = newCustomer();
        int buyers = 12;

        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < buyers; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        sell(UUID.randomUUID(), customer, last, 1);
                        succeeded.incrementAndGet();
                    } catch (InsufficientStockException e) {
                        rejected.incrementAndGet();
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(7);
        assertThat(stockOf(last)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sales", Integer.class)).isEqualTo(5);
    }

    @Test
    void inactiveProductCannotBeSold() {
        Product product = newProduct("INAT-1", "1.00", 10, 0);
        productService.update(product.getId(), "Descontinuado", product.getPrice(), 0, false);

        assertThatThrownBy(() -> sell(UUID.randomUUID(), newCustomer(), product, 1))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("inativo");
        assertThat(stockOf(product)).isEqualTo(10);
    }

    @Test
    void saleWithoutItemsOrWithInvalidQuantityIsRejected() {
        Customer customer = newCustomer();
        Product product = newProduct("QTD-1", "1.00", 10, 0);

        assertThatThrownBy(() -> saleService.create(UUID.randomUUID(), customer.getId(), PaymentMethod.CASH, List.of(), "v"))
                .isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> sell(UUID.randomUUID(), customer, product, 0))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void cancelReturnsItemsToStockAndWritesMovements() {
        Product product = newProduct("CAN-1", "3.00", 10, 0);
        UUID saleId = sell(UUID.randomUUID(), newCustomer(), product, 4).detail().sale().getId();
        assertThat(stockOf(product)).isEqualTo(6);

        SaleDetail canceled = saleService.cancel(saleId, "admin");

        assertThat(canceled.sale().getStatus()).isEqualTo(SaleStatus.CANCELED);
        assertThat(stockOf(product)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE type = 'SALE_CANCEL'", Integer.class)).isEqualTo(1);
    }

    @Test
    void cancelingTwiceOrAtTheSameTimeRestocksOnce() throws Exception {
        Product product = newProduct("CAN-2", "3.00", 10, 0);
        UUID saleId = sell(UUID.randomUUID(), newCustomer(), product, 4).detail().sale().getId();

        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Future<SaleDetail>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                futures.add(pool.submit(() -> saleService.cancel(saleId, "admin")));
            }
            for (Future<SaleDetail> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        saleService.cancel(saleId, "admin");

        assertThat(stockOf(product)).isEqualTo(10); // 6 + 4, e nao 6 + 4 * varias vezes
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movements WHERE type = 'SALE_CANCEL'", Integer.class)).isEqualTo(1);
    }
}
