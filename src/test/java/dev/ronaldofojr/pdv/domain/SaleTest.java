package dev.ronaldofojr.pdv.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SaleTest {

    private Sale newSale() {
        return new Sale(UUID.randomUUID(), UUID.randomUUID(), PaymentMethod.PIX, new BigDecimal("100.00"), "vendedor");
    }

    @Test
    void startsCompleted() {
        Sale sale = newSale();

        assertThat(sale.getStatus()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(sale.getCanceledAt()).isNull();
    }

    @Test
    void cancelReturnsTrueOnlyTheFirstTime() {
        Sale sale = newSale();

        assertThat(sale.cancel()).isTrue();
        assertThat(sale.cancel()).isFalse();
        assertThat(sale.getStatus()).isEqualTo(SaleStatus.CANCELED);
        assertThat(sale.getCanceledAt()).isNotNull();
    }

    @Test
    void saleItemKeepsASnapshotOfNameAndPrice() {
        Product product = new Product("abc-1", "Parafuso", new BigDecimal("2.50"), 10, 2);
        SaleItem item = new SaleItem(UUID.randomUUID(), product, 4);

        product.update("Parafuso Novo", new BigDecimal("9.99"), 2, true);

        assertThat(item.getProductName()).isEqualTo("Parafuso");
        assertThat(item.getUnitPrice()).isEqualByComparingTo("2.50");
        assertThat(item.getLineTotal()).isEqualByComparingTo("10.00");
    }

    @Test
    void productNormalizesSkuAndFlagsLowStock() {
        Product product = new Product("  abc-1 ", "Parafuso", new BigDecimal("2.5"), 3, 5);

        assertThat(product.getSku()).isEqualTo("ABC-1");
        assertThat(product.getPrice().scale()).isEqualTo(2);
        assertThat(product.isLowStock()).isTrue();
    }
}
