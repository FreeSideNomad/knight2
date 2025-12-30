package com.knight.application.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for JWT authentication.
 * Supports multiple issuers (Entra ID and Employee Portal).
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * Whether JWT authentication is enabled.
     * When false, all requests are permitted (for development/testing).
     */
    private boolean enabled = true;

    /**
     * Entra ID (Azure AD) configuration.
     */
    private EntraConfig entra = new EntraConfig();

    /**
     * Employee Portal JWT configuration.
     */
    private PortalConfig portal = new PortalConfig();

    /**
     * Auth0 JWT configuration for external client authentication.
     */
    private Auth0Config auth0 = new Auth0Config();

    @Getter
    @Setter
    public static class EntraConfig {
        /**
         * Azure AD tenant ID.
         */
        private String tenantId;

        /**
         * Azure AD client ID (audience).
         */
        private String clientId;

        /**
         * Whether Entra ID authentication is enabled.
         */
        private boolean enabled = true;

        /**
         * Get the issuer URL for Entra ID v2.0.
         */
        public String getIssuerUri() {
            return "https://login.microsoftonline.com/" + tenantId + "/v2.0";
        }

        /**
         * Get the JWKS URI for Entra ID.
         */
        public String getJwksUri() {
            return "https://login.microsoftonline.com/" + tenantId + "/discovery/v2.0/keys";
        }
    }

    @Getter
    @Setter
    public static class PortalConfig {
        /**
         * Employee Portal issuer URL.
         */
        private String issuer = "http://localhost:8081";

        /**
         * JWKS endpoint URL for Employee Portal.
         */
        private String jwksUri = "http://localhost:8081/.well-known/jwks.json";

        /**
         * Expected audience for portal tokens.
         */
        private String audience = "knight-platform-api";

        /**
         * Whether Portal JWT authentication is enabled.
         */
        private boolean enabled = true;
    }

    @Getter
    @Setter
    public static class Auth0Config {
        /**
         * Auth0 domain (e.g., "dbc-test.auth0.com").
         */
        private String domain;

        /**
         * Expected API audience for Auth0 tokens.
         */
        private String audience;

        /**
         * Whether Auth0 JWT authentication is enabled.
         */
        private boolean enabled = true;

        /**
         * Get the issuer URL for Auth0.
         */
        public String getIssuerUri() {
            return "https://" + domain + "/";
        }

        /**
         * Get the JWKS URI for Auth0.
         */
        public String getJwksUri() {
            return "https://" + domain + "/.well-known/jwks.json";
        }
    }
}
