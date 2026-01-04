package com.knight.domain.auth0identity.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Auth0 API exceptions.
 */
class ExceptionTest {

    @Nested
    @DisplayName("Auth0IntegrationException")
    class Auth0IntegrationExceptionTests {

        @Test
        @DisplayName("should create exception with message")
        void shouldCreateExceptionWithMessage() {
            Auth0IntegrationException exception = new Auth0IntegrationException("Test error");

            assertThat(exception.getMessage()).isEqualTo("Test error");
            assertThat(exception.getCause()).isNull();
        }

        @Test
        @DisplayName("should create exception with message and cause")
        void shouldCreateExceptionWithMessageAndCause() {
            Throwable cause = new RuntimeException("Root cause");
            Auth0IntegrationException exception = new Auth0IntegrationException("Test error", cause);

            assertThat(exception.getMessage()).isEqualTo("Test error");
            assertThat(exception.getCause()).isEqualTo(cause);
        }
    }

    @Nested
    @DisplayName("UserAlreadyExistsException")
    class UserAlreadyExistsExceptionTests {

        @Test
        @DisplayName("should create exception with message only")
        void shouldCreateExceptionWithMessageOnly() {
            UserAlreadyExistsException exception = new UserAlreadyExistsException("User exists");

            assertThat(exception.getMessage()).isEqualTo("User exists");
            assertThat(exception.getEmail()).isNull();
            assertThat(exception.getExistingUserId()).isNull();
        }

        @Test
        @DisplayName("should create exception with email and user ID")
        void shouldCreateExceptionWithEmailAndUserId() {
            String email = "test@example.com";
            String userId = "auth0|123";

            UserAlreadyExistsException exception = new UserAlreadyExistsException(email, userId);

            assertThat(exception.getMessage()).isEqualTo("User already exists in Auth0: test@example.com (ID: auth0|123)");
            assertThat(exception.getEmail()).isEqualTo(email);
            assertThat(exception.getExistingUserId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("should extend Auth0IntegrationException")
        void shouldExtendAuth0IntegrationException() {
            UserAlreadyExistsException exception = new UserAlreadyExistsException("test");

            assertThat(exception).isInstanceOf(Auth0IntegrationException.class);
            assertThat(exception).isInstanceOf(RuntimeException.class);
        }
    }
}
