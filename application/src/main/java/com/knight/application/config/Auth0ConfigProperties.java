package com.knight.application.config;

import com.knight.domain.auth0identity.config.Auth0Config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Auth0 identity provider.
 */
@Configuration
@ConfigurationProperties(prefix = "auth0")
public class Auth0ConfigProperties {

    private String domain = "knight.auth0.com";
    private String clientId = "";
    private String clientSecret = "";
    private String audience = "";
    private String managementAudience = "";
    private String connection = "Username-Password-Authentication";

    @Bean
    public Auth0Config auth0Config() {
        return new Auth0Config(
            domain,
            clientId,
            clientSecret,
            audience,
            managementAudience,
            connection
        );
    }

    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }
    public String getAudience() { return audience; }
    public void setAudience(String audience) { this.audience = audience; }
    public String getManagementAudience() { return managementAudience; }
    public void setManagementAudience(String managementAudience) { this.managementAudience = managementAudience; }
    public String getConnection() { return connection; }
    public void setConnection(String connection) { this.connection = connection; }
}
