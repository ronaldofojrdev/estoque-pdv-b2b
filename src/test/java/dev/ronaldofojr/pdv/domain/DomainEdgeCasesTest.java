package dev.ronaldofojr.pdv.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Casos de borda que o caminho feliz nao cobre. */
class DomainEdgeCasesTest {

    @ParameterizedTest
    @ValueSource(strings = {"1122233300018", "112223330001811", "11.222.333/0001-8", "ab.cde.fgh/ijkl-mn", "11 222 333 0001 81x", "x11222333000181"})
    void cnpjWithWrongLengthOrLettersIsInvalid(String value) {
        assertThat(Cnpj.isValid(value)).isFalse();
    }

    @Test
    void cnpjWithSpacesAroundTheDigitsIsStillValid() {
        assertThat(Cnpj.isValid("  11.222.333/0001-81  ")).isTrue();
    }

    @Test
    void cnpjWithAllTheSameDigitIsInvalidEvenWhenTheCheckDigitsMatch() {
        for (char digit = '0'; digit <= '9'; digit++) {
            assertThat(Cnpj.isValid(String.valueOf(digit).repeat(14))).as("cnpj " + digit).isFalse();
        }
    }

    @Test
    void productCanStartWithZeroStockAndZeroMinimum() {
        Product product = new Product("ZERO-1", "Produto zerado", new BigDecimal("0.00"), 0, 0);

        assertThat(product.getQuantity()).isZero();
        assertThat(product.isLowStock()).as("0 <= 0: ja conta como estoque baixo").isTrue();
    }

    @Test
    void saleItemWithTheMinimumQuantityOfOneHasTheSameLineTotalAsThePrice() {
        Product product = new Product("UM-1", "Produto", new BigDecimal("12.34"), 5, 0);

        assertThat(new SaleItem(java.util.UUID.randomUUID(), product, 1).getLineTotal()).isEqualByComparingTo("12.34");
    }

    @Test
    void paymentMethodsAndRolesAreTheExpectedOnes() {
        assertThat(PaymentMethod.values()).containsExactly(PaymentMethod.CASH, PaymentMethod.PIX, PaymentMethod.CARD, PaymentMethod.INVOICE);
        assertThat(Role.values()).containsExactly(Role.ADMIN, Role.SELLER);
    }

    @Test
    void customerStoresOnlyTheCnpjDigits() {
        Customer customer = new Customer("Empresa", "11.222.333/0001-81", null);

        assertThat(customer.getCnpj()).isEqualTo("11222333000181");
    }

    @Test
    void negativeValuesAreRejectedByTheProduct() {
        assertThatThrownBy(() -> new Product("NEG-1", "x", new BigDecimal("-0.01"), 1, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("preco");
        assertThatThrownBy(() -> new Product("NEG-2", "x", BigDecimal.ONE, -1, 0))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("estoque");
        assertThatThrownBy(() -> new Product("NEG-3", "x", BigDecimal.ONE, 1, -1))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("minimo");
        Product product = new Product("NEG-4", "x", BigDecimal.ONE, 1, 0);
        assertThatThrownBy(() -> product.update("x", new BigDecimal("-5"), 0, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
