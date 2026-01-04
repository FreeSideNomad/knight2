package com.knight.domain.auth0identity.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Auth0Config.
 */
class Auth0ConfigTest {

    private static final String DOMAIN = "example.auth0.com";
    private static final String CLIENT_ID = "client-123";
    private static final String CLIENT_SECRET = "secret-456";
    private static final String AUDIENCE = "https://api.example.com";
    private static final String MANAGEMENT_AUDIENCE = "https://example.auth0.com/api/v2/";
    private static final String CONNECTION = "Username-Password-Authentication";
    private static final String PASSWORD_RESET_URL = "https://app.example.com/reset";

    @Test
    @DisplayName("should create config with all properties")
    void shouldCreateConfigWithAllProperties() {
        Auth0Config config = new Auth0Config(
            DOMAIN, CLIENT_ID, CLIENT_SECRET, AUDIENCE,
            MANAGEMENT_AUDIENCE, CONNECTION, PASSWORD_RESET_URL
        );

        assertThat(config.domain()).isEqualTo(DOMAIN);
        assertThat(config.clientId()).isEqualTo(CLIENT_ID);
        assertThat(config.clientSecret()).isEqualTo(CLIENT_SECRET);
        assertThat(config.audience()).isEqualTo(AUDIENCE);
        assertThat(config.managementAudience()).isEqualTo(MANAGEMENT_AUDIENCE);
        assertThat(config.connection()).isEqualTo(CONNECTION);
        assertThat(config.passwordResetResultUrl()).isEqualTo(PASSWORD_RESET_URL);
    }

    @Test
    @DisplayName("getIssuer should return https URL with trailing slash")
    void getIssuerShouldReturnHttpsUrlWithTrailingSlash() {
        Auth0Config config = createConfig(PASSWORD_RESET_URL);

        String issuer = config.getIssuer();

        assertThat(issuer).isEqualTo("https://example.auth0.com/");
    }

    @Test
    @DisplayName("getManagementApiUrl should return API v2 URL")
    void getManagementApiUrlShouldReturnApiV2Url() {
        Auth0Config config = createConfig(PASSWORD_RESET_URL);

        String apiUrl = config.getManagementApiUrl();

        assertThat(apiUrl).isEqualTo("https://example.auth0.com/api/v2");
    }

    @Test
    @DisplayName("getTokenUrl should return OAuth token URL")
    void getTokenUrlShouldReturnOAuthTokenUrl() {
        Auth0Config config = createConfig(PASSWORD_RESET_URL);

        String tokenUrl = config.getTokenUrl();

        assertThat(tokenUrl).isEqualTo("https://example.auth0.com/oauth/token");
    }

    @Test
    @DisplayName("getPasswordResetResultUrl should return configured URL")
    void getPasswordResetResultUrlShouldReturnConfiguredUrl() {
        Auth0Config config = createConfig(PASSWORD_RESET_URL);

        String url = config.getPasswordResetResultUrl();

        assertThat(url).isEqualTo(PASSWORD_RESET_URL);
    }

    @Test
    @DisplayName("getPasswordResetResultUrl should return default when null")
    void getPasswordResetResultUrlShouldReturnDefaultWhenNull() {
        Auth0Config config = createConfig(null);

        String url = config.getPasswordResetResultUrl();

        assertThat(url).isEqualTo("http://localhost/");
    }

    private Auth0Config createConfig(String passwordResetUrl) {
        return new Auth0Config(
            DOMAIN, CLIENT_ID, CLIENT_SECRET, AUDIENCE,
            MANAGEMENT_AUDIENCE, CONNECTION, passwordResetUrl
        );
    }
}
