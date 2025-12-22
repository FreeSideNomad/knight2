package com.knight.domain.auth0identity.config;

/**
 * Auth0 configuration properties.
 */
public record Auth0Config(
    String domain,
    String clientId,
    String clientSecret,
    String audience,
    String managementAudience,
    String connection,
    String passwordResetResultUrl
) {
    public String getIssuer() {
        return "https://" + domain + "/";
    }

    public String getManagementApiUrl() {
        return "https://" + domain + "/api/v2";
    }

    public String getTokenUrl() {
        return "https://" + domain + "/oauth/token";
    }

    public String getPasswordResetResultUrl() {
        return passwordResetResultUrl != null ? passwordResetResultUrl : "http://localhost/";
    }
}
