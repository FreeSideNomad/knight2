package com.knight.application.service.email;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for EmailProperties.
 */
class EmailPropertiesTest {

    private EmailProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EmailProperties();
    }

    @Nested
    @DisplayName("Default Values")
    class DefaultValueTests {

        @Test
        @DisplayName("should have mock as default provider")
        void shouldHaveMockAsDefaultProvider() {
            assertThat(properties.getProvider()).isEqualTo("mock");
        }

        @Test
        @DisplayName("should have default from address")
        void shouldHaveDefaultFromAddress() {
            assertThat(properties.getFromAddress()).isEqualTo("noreply@knight.example.com");
        }

        @Test
        @DisplayName("should have default from name")
        void shouldHaveDefaultFromName() {
            assertThat(properties.getFromName()).isEqualTo("Knight Platform");
        }

        @Test
        @DisplayName("should have non-null AhaSend config")
        void shouldHaveNonNullAhaSendConfig() {
            assertThat(properties.getAhasend()).isNotNull();
        }

        @Test
        @DisplayName("should have non-null SMTP config")
        void shouldHaveNonNullSmtpConfig() {
            assertThat(properties.getSmtp()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Setters")
    class SetterTests {

        @Test
        @DisplayName("should set provider")
        void shouldSetProvider() {
            properties.setProvider("ahasend");
            assertThat(properties.getProvider()).isEqualTo("ahasend");
        }

        @Test
        @DisplayName("should set from address")
        void shouldSetFromAddress() {
            properties.setFromAddress("custom@example.com");
            assertThat(properties.getFromAddress()).isEqualTo("custom@example.com");
        }

        @Test
        @DisplayName("should set from name")
        void shouldSetFromName() {
            properties.setFromName("Custom Name");
            assertThat(properties.getFromName()).isEqualTo("Custom Name");
        }

        @Test
        @DisplayName("should set AhaSend config")
        void shouldSetAhaSendConfig() {
            EmailProperties.AhaSend ahaSend = new EmailProperties.AhaSend();
            ahaSend.setAccountId("new-account");
            properties.setAhasend(ahaSend);
            assertThat(properties.getAhasend().getAccountId()).isEqualTo("new-account");
        }

        @Test
        @DisplayName("should set SMTP config")
        void shouldSetSmtpConfig() {
            EmailProperties.Smtp smtp = new EmailProperties.Smtp();
            smtp.setHost("smtp.example.com");
            properties.setSmtp(smtp);
            assertThat(properties.getSmtp().getHost()).isEqualTo("smtp.example.com");
        }
    }

    @Nested
    @DisplayName("AhaSend Configuration")
    class AhaSendConfigTests {

        @Test
        @DisplayName("should have default API URL")
        void shouldHaveDefaultApiUrl() {
            assertThat(properties.getAhasend().getApiUrl()).isEqualTo("https://api.ahasend.com/v2");
        }

        @Test
        @DisplayName("should set account id")
        void shouldSetAccountId() {
            properties.getAhasend().setAccountId("test-account");
            assertThat(properties.getAhasend().getAccountId()).isEqualTo("test-account");
        }

        @Test
        @DisplayName("should set api key")
        void shouldSetApiKey() {
            properties.getAhasend().setApiKey("test-api-key");
            assertThat(properties.getAhasend().getApiKey()).isEqualTo("test-api-key");
        }

        @Test
        @DisplayName("should set api url")
        void shouldSetApiUrl() {
            properties.getAhasend().setApiUrl("https://custom.api.com");
            assertThat(properties.getAhasend().getApiUrl()).isEqualTo("https://custom.api.com");
        }

        @Test
        @DisplayName("should be configured when both account id and api key are set")
        void shouldBeConfiguredWhenBothSet() {
            properties.getAhasend().setAccountId("test-account");
            properties.getAhasend().setApiKey("test-key");

            assertThat(properties.getAhasend().isConfigured()).isTrue();
        }

        @Test
        @DisplayName("should not be configured when account id is null")
        void shouldNotBeConfiguredWhenAccountIdNull() {
            properties.getAhasend().setAccountId(null);
            properties.getAhasend().setApiKey("test-key");

            assertThat(properties.getAhasend().isConfigured()).isFalse();
        }

        @Test
        @DisplayName("should not be configured when account id is blank")
        void shouldNotBeConfiguredWhenAccountIdBlank() {
            properties.getAhasend().setAccountId("   ");
            properties.getAhasend().setApiKey("test-key");

            assertThat(properties.getAhasend().isConfigured()).isFalse();
        }

        @Test
        @DisplayName("should not be configured when api key is null")
        void shouldNotBeConfiguredWhenApiKeyNull() {
            properties.getAhasend().setAccountId("test-account");
            properties.getAhasend().setApiKey(null);

            assertThat(properties.getAhasend().isConfigured()).isFalse();
        }

        @Test
        @DisplayName("should not be configured when api key is blank")
        void shouldNotBeConfiguredWhenApiKeyBlank() {
            properties.getAhasend().setAccountId("test-account");
            properties.getAhasend().setApiKey("");

            assertThat(properties.getAhasend().isConfigured()).isFalse();
        }
    }

    @Nested
    @DisplayName("SMTP Configuration")
    class SmtpConfigTests {

        @Test
        @DisplayName("should have default port 587")
        void shouldHaveDefaultPort587() {
            assertThat(properties.getSmtp().getPort()).isEqualTo(587);
        }

        @Test
        @DisplayName("should have starttls enabled by default")
        void shouldHaveStarttlsEnabledByDefault() {
            assertThat(properties.getSmtp().isStarttls()).isTrue();
        }

        @Test
        @DisplayName("should set host")
        void shouldSetHost() {
            properties.getSmtp().setHost("smtp.example.com");
            assertThat(properties.getSmtp().getHost()).isEqualTo("smtp.example.com");
        }

        @Test
        @DisplayName("should set port")
        void shouldSetPort() {
            properties.getSmtp().setPort(465);
            assertThat(properties.getSmtp().getPort()).isEqualTo(465);
        }

        @Test
        @DisplayName("should set username")
        void shouldSetUsername() {
            properties.getSmtp().setUsername("smtp-user");
            assertThat(properties.getSmtp().getUsername()).isEqualTo("smtp-user");
        }

        @Test
        @DisplayName("should set password")
        void shouldSetPassword() {
            properties.getSmtp().setPassword("smtp-password");
            assertThat(properties.getSmtp().getPassword()).isEqualTo("smtp-password");
        }

        @Test
        @DisplayName("should set starttls")
        void shouldSetStarttls() {
            properties.getSmtp().setStarttls(false);
            assertThat(properties.getSmtp().isStarttls()).isFalse();
        }

        @Test
        @DisplayName("should be configured when host is set")
        void shouldBeConfiguredWhenHostSet() {
            properties.getSmtp().setHost("smtp.example.com");

            assertThat(properties.getSmtp().isConfigured()).isTrue();
        }

        @Test
        @DisplayName("should not be configured when host is null")
        void shouldNotBeConfiguredWhenHostNull() {
            properties.getSmtp().setHost(null);

            assertThat(properties.getSmtp().isConfigured()).isFalse();
        }

        @Test
        @DisplayName("should not be configured when host is blank")
        void shouldNotBeConfiguredWhenHostBlank() {
            properties.getSmtp().setHost("   ");

            assertThat(properties.getSmtp().isConfigured()).isFalse();
        }
    }
}
