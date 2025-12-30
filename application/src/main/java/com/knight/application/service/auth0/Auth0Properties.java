package com.knight.application.service.auth0;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth0.login")
public record Auth0Properties(
    String domain,
    String clientId,
    String clientSecret,
    String m2mClientId,
    String m2mClientSecret,
    String audience,
    String connection
) {
    public Auth0Properties {
        if (connection == null || connection.isBlank()) {
            connection = "Username-Password-Authentication";
        }
    }
}
