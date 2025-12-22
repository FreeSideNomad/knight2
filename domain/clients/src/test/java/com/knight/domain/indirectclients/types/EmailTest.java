package com.knight.domain.indirectclients.types;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Email value object.
 */
@DisplayName("Email Tests")
class EmailTest {

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should create email from valid address")
        void shouldCreateEmailFromValidAddress() {
            Email email = new Email("test@example.com");

            assertThat(email.value()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should normalize email to lowercase")
        void shouldNormalizeEmailToLowercase() {
            Email email = new Email("Test@Example.COM");

            assertThat(email.value()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should trim whitespace")
        void shouldTrimWhitespace() {
            Email email = new Email("  test@example.com  ");

            assertThat(email.value()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("should accept email with special characters")
        void shouldAcceptEmailWithSpecialCharacters() {
            Email email = new Email("test.user+tag@example.co.uk");

            assertThat(email.value()).isEqualTo("test.user+tag@example.co.uk");
        }

        @Test
        @DisplayName("should throw when email is null")
        void shouldThrowWhenEmailIsNull() {
            assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Email cannot be null");
        }

        @Test
        @DisplayName("should throw when email format is invalid - missing @")
        void shouldThrowWhenEmailMissingAt() {
            assertThatThrownBy(() -> new Email("testexample.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
        }

        @Test
        @DisplayName("should throw when email format is invalid - missing domain")
        void shouldThrowWhenEmailMissingDomain() {
            assertThatThrownBy(() -> new Email("test@"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
        }

        @Test
        @DisplayName("should throw when email format is invalid - missing local part")
        void shouldThrowWhenEmailMissingLocalPart() {
            assertThatThrownBy(() -> new Email("@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
        }

        @Test
        @DisplayName("should throw when email format is invalid - missing TLD")
        void shouldThrowWhenEmailMissingTld() {
            assertThatThrownBy(() -> new Email("test@example"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid email format");
        }
    }

    @Nested
    @DisplayName("of() factory method")
    class OfFactoryMethod {

        @Test
        @DisplayName("should create email using of method")
        void shouldCreateEmailUsingOfMethod() {
            Email email = Email.of("test@example.com");

            assertThat(email.value()).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("toString()")
    class ToStringMethod {

        @Test
        @DisplayName("should return value")
        void shouldReturnValue() {
            Email email = new Email("test@example.com");

            assertThat(email.toString()).isEqualTo("test@example.com");
        }
    }
}
