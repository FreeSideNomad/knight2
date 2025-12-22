package com.knight.domain.clients.api.queries;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ClientSearchQuery.
 */
@DisplayName("ClientSearchQuery Tests")
class ClientSearchQueryTest {

    @Nested
    @DisplayName("constructor validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should create query with srf client type")
        void shouldCreateQueryWithSrfClientType() {
            ClientSearchQuery query = new ClientSearchQuery("srf", "Test Client");

            assertThat(query.clientType()).isEqualTo("srf");
            assertThat(query.nameQuery()).isEqualTo("Test Client");
        }

        @Test
        @DisplayName("should create query with cdr client type")
        void shouldCreateQueryWithCdrClientType() {
            ClientSearchQuery query = new ClientSearchQuery("cdr", "Test Client");

            assertThat(query.clientType()).isEqualTo("cdr");
            assertThat(query.nameQuery()).isEqualTo("Test Client");
        }

        @Test
        @DisplayName("should trim nameQuery whitespace")
        void shouldTrimNameQueryWhitespace() {
            ClientSearchQuery query = new ClientSearchQuery("srf", "  Test Client  ");

            assertThat(query.nameQuery()).isEqualTo("Test Client");
        }

        @Test
        @DisplayName("should throw when clientType is null")
        void shouldThrowWhenClientTypeIsNull() {
            assertThatThrownBy(() -> new ClientSearchQuery(null, "Test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Client type cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when clientType is blank")
        void shouldThrowWhenClientTypeIsBlank() {
            assertThatThrownBy(() -> new ClientSearchQuery("  ", "Test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Client type cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when clientType is empty")
        void shouldThrowWhenClientTypeIsEmpty() {
            assertThatThrownBy(() -> new ClientSearchQuery("", "Test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Client type cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when clientType is invalid")
        void shouldThrowWhenClientTypeIsInvalid() {
            assertThatThrownBy(() -> new ClientSearchQuery("invalid", "Test"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid client type: invalid")
                .hasMessageContaining("Must be 'srf' or 'cdr'");
        }

        @Test
        @DisplayName("should throw when nameQuery is null")
        void shouldThrowWhenNameQueryIsNull() {
            assertThatThrownBy(() -> new ClientSearchQuery("srf", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name query cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when nameQuery is blank")
        void shouldThrowWhenNameQueryIsBlank() {
            assertThatThrownBy(() -> new ClientSearchQuery("srf", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name query cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when nameQuery is empty")
        void shouldThrowWhenNameQueryIsEmpty() {
            assertThatThrownBy(() -> new ClientSearchQuery("srf", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Name query cannot be null or blank");
        }
    }
}
