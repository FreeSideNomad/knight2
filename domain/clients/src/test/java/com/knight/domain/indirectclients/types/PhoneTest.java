package com.knight.domain.indirectclients.types;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Phone value object.
 */
@DisplayName("Phone Tests")
class PhoneTest {

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should create phone from digits only")
        void shouldCreatePhoneFromDigitsOnly() {
            Phone phone = new Phone("1234567890");

            assertThat(phone.value()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("should create phone from formatted number with spaces")
        void shouldCreatePhoneFromFormattedNumberWithSpaces() {
            Phone phone = new Phone("123 456 7890");

            assertThat(phone.value()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("should create phone from formatted number with hyphens")
        void shouldCreatePhoneFromFormattedNumberWithHyphens() {
            Phone phone = new Phone("123-456-7890");

            assertThat(phone.value()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("should create phone from formatted number with parentheses")
        void shouldCreatePhoneFromFormattedNumberWithParentheses() {
            Phone phone = new Phone("(123) 456-7890");

            assertThat(phone.value()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("should normalize multiple spaces to single space")
        void shouldNormalizeMultipleSpacesToSingleSpace() {
            Phone phone = new Phone("123   456   7890");

            assertThat(phone.value()).isEqualTo("1234567890");
        }

        @Test
        @DisplayName("should throw when phone is null")
        void shouldThrowWhenPhoneIsNull() {
            assertThatThrownBy(() -> new Phone(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Phone cannot be null");
        }

        @Test
        @DisplayName("should throw when phone contains invalid characters")
        void shouldThrowWhenPhoneContainsInvalidCharacters() {
            assertThatThrownBy(() -> new Phone("123-456-7890abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone can only contain digits, spaces, hyphens, and parentheses");
        }

        @Test
        @DisplayName("should throw when phone has less than 7 digits")
        void shouldThrowWhenPhoneHasLessThan7Digits() {
            assertThatThrownBy(() -> new Phone("123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone number must be between 7 and 15 digits");
        }

        @Test
        @DisplayName("should throw when phone has more than 15 digits")
        void shouldThrowWhenPhoneHasMoreThan15Digits() {
            assertThatThrownBy(() -> new Phone("1234567890123456"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Phone number must be between 7 and 15 digits");
        }

        @Test
        @DisplayName("should accept 7 digit phone number")
        void shouldAccept7DigitPhoneNumber() {
            Phone phone = new Phone("1234567");

            assertThat(phone.value()).isEqualTo("1234567");
        }

        @Test
        @DisplayName("should accept 15 digit phone number")
        void shouldAccept15DigitPhoneNumber() {
            Phone phone = new Phone("123456789012345");

            assertThat(phone.value()).isEqualTo("123456789012345");
        }
    }

    @Nested
    @DisplayName("of() factory method")
    class OfFactoryMethod {

        @Test
        @DisplayName("should create phone using of method")
        void shouldCreatePhoneUsingOfMethod() {
            Phone phone = Phone.of("1234567890");

            assertThat(phone.value()).isEqualTo("1234567890");
        }
    }

    @Nested
    @DisplayName("formatted()")
    class Formatted {

        @Test
        @DisplayName("should format 10 digit phone number")
        void shouldFormat10DigitPhoneNumber() {
            Phone phone = new Phone("1234567890");

            assertThat(phone.formatted()).isEqualTo("(123) 456-7890");
        }

        @Test
        @DisplayName("should return value for non-10 digit phone number")
        void shouldReturnValueForNon10DigitPhoneNumber() {
            Phone phone = new Phone("12345678901");

            assertThat(phone.formatted()).isEqualTo("12345678901");
        }

        @Test
        @DisplayName("should return value for 7 digit phone number")
        void shouldReturnValueFor7DigitPhoneNumber() {
            Phone phone = new Phone("1234567");

            assertThat(phone.formatted()).isEqualTo("1234567");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should return value")
        void shouldReturnValue() {
            Phone phone = new Phone("1234567890");

            assertThat(phone.toString()).isEqualTo("1234567890");
        }
    }
}
