package com.knight.indirectportal.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT configuration properties for Auth0 validation.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String issuer;
    private String audience;
    private String jwksUrl;
}
