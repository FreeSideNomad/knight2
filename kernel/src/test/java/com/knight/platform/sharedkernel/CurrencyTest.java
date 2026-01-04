package com.knight.platform.sharedkernel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrencyTest {

    @Test
    void constructor_withValidCode_createsCurrency() {
        Currency currency = new Currency("CAD");
        assertThat(currency.code()).isEqualTo("CAD");
    }

    @Test
    void of_withValidCode_createsCurrency() {
        Currency currency = Currency.of("USD");
        assertThat(currency.code()).isEqualTo("USD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAD", "USD", "EUR", "GBP", "JPY", "AUD", "CHF", "CNY",
            "HKD", "NZD", "SEK", "KRW", "SGD", "NOK", "MXN", "INR", "BRL", "ZAR"})
    void constructor_withAllValidCodes_createsCurrency(String code) {
        Currency currency = new Currency(code);
        assertThat(currency.code()).isEqualTo(code);
    }

    @Test
    void constructor_withNullCode_throwsException() {
        assertThatThrownBy(() -> new Currency(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency code cannot be null or blank");
    }

    @Test
    void constructor_withBlankCode_throwsException() {
        assertThatThrownBy(() -> new Currency("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency code cannot be null or blank");
    }

    @Test
    void constructor_withEmptyCode_throwsException() {
        assertThatThrownBy(() -> new Currency(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency code cannot be null or blank");
    }

    @Test
    void constructor_withTwoCharacterCode_throwsException() {
        assertThatThrownBy(() -> new Currency("CA"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency code must be exactly 3 characters, got: 2");
    }

    @Test
    void constructor_withFourCharacterCode_throwsException() {
        assertThatThrownBy(() -> new Currency("CADD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency code must be exactly 3 characters, got: 4");
    }

    @Test
    void constructor_withLowercaseCode_throwsException() {
        assertThatThrownBy(() -> new Currency("cad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency code must be uppercase, got: cad");
    }

    @Test
    void constructor_withMixedCaseCode_throwsException() {
        assertThatThrownBy(() -> new Currency("Cad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency code must be uppercase, got: Cad");
    }

    @Test
    void constructor_withNumericCode_throwsException() {
        assertThatThrownBy(() -> new Currency("123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Currency code must contain only uppercase letters, got: 123");
    }

    @Test
    void constructor_withInvalidIsoCode_throwsException() {
        assertThatThrownBy(() -> new Currency("XYZ"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid ISO 4217 currency code: XYZ");
    }

    @Test
    void constants_areCorrect() {
        assertThat(Currency.CAD.code()).isEqualTo("CAD");
        assertThat(Currency.USD.code()).isEqualTo("USD");
        assertThat(Currency.EUR.code()).isEqualTo("EUR");
        assertThat(Currency.GBP.code()).isEqualTo("GBP");
    }

    @Test
    void equals_sameCodes_areEqual() {
        Currency c1 = new Currency("CAD");
        Currency c2 = new Currency("CAD");
        assertThat(c1).isEqualTo(c2);
    }

    @Test
    void equals_differentCodes_areNotEqual() {
        Currency c1 = new Currency("CAD");
        Currency c2 = new Currency("USD");
        assertThat(c1).isNotEqualTo(c2);
    }

    @Test
    void hashCode_sameCodes_sameHash() {
        Currency c1 = new Currency("CAD");
        Currency c2 = new Currency("CAD");
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }
}
