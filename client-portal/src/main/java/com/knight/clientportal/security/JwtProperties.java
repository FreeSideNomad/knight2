package com.knight.clientportal.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
    String issuer,
    String audience,
    String jwksUrl
) {}
