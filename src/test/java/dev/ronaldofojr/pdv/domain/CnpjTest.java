package dev.ronaldofojr.pdv.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CnpjTest {

    @ParameterizedTest
    @ValueSource(strings = {"11222333000181", "11.222.333/0001-81", "04.252.011/0001-10", "00.000.000/0001-91"})
    void acceptsValidCnpjWithOrWithoutPunctuation(String value) {
        assertThat(Cnpj.isValid(value)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11222333000182", "11.222.333/0001-80", "123", "", "abcdefghijklmn", "11111111111111", "00000000000000"})
    void rejectsInvalidCnpj(String value) {
        assertThat(Cnpj.isValid(value)).isFalse();
    }

    @Test
    void rejectsNull() {
        assertThat(Cnpj.isValid(null)).isFalse();
    }

    @Test
    void normalizeKeepsOnlyDigits() {
        assertThat(Cnpj.normalize("11.222.333/0001-81")).isEqualTo("11222333000181");
        assertThat(Cnpj.normalize(null)).isEmpty();
    }
}
