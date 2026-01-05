package com.knight.application.service.otp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OtpProperties.
 */
class OtpPropertiesTest {

    private OtpProperties properties;

    @BeforeEach
    void setUp() {
        properties = new OtpProperties();
    }

    @Nested
    @DisplayName("Default Values")
    class DefaultValueTests {

        @Test
        @DisplayName("should have default expiration of 120 seconds")
        void shouldHaveDefaultExpiration() {
            assertThat(properties.getExpirationSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should have default max attempts of 3")
        void shouldHaveDefaultMaxAttempts() {
            assertThat(properties.getMaxAttempts()).isEqualTo(3);
        }

        @Test
        @DisplayName("should have default rate limit window of 60 seconds")
        void shouldHaveDefaultRateLimitWindow() {
            assertThat(properties.getRateLimitWindowSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("should have default rate limit max requests of 3")
        void shouldHaveDefaultRateLimitMaxRequests() {
            assertThat(properties.getRateLimitMaxRequests()).isEqualTo(3);
        }

        @Test
        @DisplayName("should have default resend cooldown of 30 seconds")
        void shouldHaveDefaultResendCooldown() {
            assertThat(properties.getResendCooldownSeconds()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("should set expiration seconds")
        void shouldSetExpirationSeconds() {
            properties.setExpirationSeconds(300);
            assertThat(properties.getExpirationSeconds()).isEqualTo(300);
        }

        @Test
        @DisplayName("should set max attempts")
        void shouldSetMaxAttempts() {
            properties.setMaxAttempts(5);
            assertThat(properties.getMaxAttempts()).isEqualTo(5);
        }

        @Test
        @DisplayName("should set rate limit window seconds")
        void shouldSetRateLimitWindowSeconds() {
            properties.setRateLimitWindowSeconds(120);
            assertThat(properties.getRateLimitWindowSeconds()).isEqualTo(120);
        }

        @Test
        @DisplayName("should set rate limit max requests")
        void shouldSetRateLimitMaxRequests() {
            properties.setRateLimitMaxRequests(5);
            assertThat(properties.getRateLimitMaxRequests()).isEqualTo(5);
        }

        @Test
        @DisplayName("should set resend cooldown seconds")
        void shouldSetResendCooldownSeconds() {
            properties.setResendCooldownSeconds(60);
            assertThat(properties.getResendCooldownSeconds()).isEqualTo(60);
        }
    }
}
