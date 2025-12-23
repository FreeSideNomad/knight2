package com.knight.portal.config;

import com.knight.portal.security.jwt.GatewayTokenHolder;
import com.knight.portal.security.jwt.JwtTokenService;
import com.knight.portal.security.ldap.LdapAuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Configuration for API clients (RestClient and WebClient) with JWT authentication.
 * Supports both pass-through (gateway) and locally-generated tokens.
 */
@Slf4j
@Configuration
public class ApiClientConfig {

    @Value("${api.base-url:http://localhost:8080}")
    private String apiBaseUrl;

    private final JwtTokenService jwtTokenService;
    private final GatewayTokenHolder gatewayTokenHolder;

    public ApiClientConfig(JwtTokenService jwtTokenService, GatewayTokenHolder gatewayTokenHolder) {
        this.jwtTokenService = jwtTokenService;
        this.gatewayTokenHolder = gatewayTokenHolder;
    }

    @Bean
    public RestClient apiRestClient() {
        return RestClient.builder()
                .baseUrl(apiBaseUrl)
                .requestInterceptor(jwtInterceptor())
                .build();
    }

    @Bean
    public WebClient apiWebClient() {
        return WebClient.builder()
                .baseUrl(apiBaseUrl)
                .filter(jwtExchangeFilter())
                .build();
    }

    /**
     * Interceptor for RestClient that adds JWT Authorization header.
     */
    private ClientHttpRequestInterceptor jwtInterceptor() {
        return (request, body, execution) -> {
            String token = resolveToken();
            if (token != null) {
                request.getHeaders().setBearerAuth(token);
                log.debug("Added JWT token to RestClient request");
            }
            return execution.execute(request, body);
        };
    }

    /**
     * Filter for WebClient that adds JWT Authorization header.
     */
    private ExchangeFilterFunction jwtExchangeFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            String token = resolveToken();
            if (token != null) {
                log.debug("Added JWT token to WebClient request");
                return Mono.just(ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build());
            }
            return Mono.just(request);
        });
    }

    /**
     * Resolve the JWT token to use for API calls.
     * Priority:
     * 1. Pass-through token from gateway (if present)
     * 2. Generate new token from LDAP user
     */
    private String resolveToken() {
        // Check for pass-through token from gateway
        if (gatewayTokenHolder.hasToken()) {
            log.debug("Using pass-through token from gateway");
            return gatewayTokenHolder.getToken();
        }

        // Generate token from authenticated LDAP user
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LdapAuthenticatedUser user) {
            log.debug("Generating JWT token for LDAP user: {}", user.getEmail());
            return jwtTokenService.generateToken(user);
        }

        log.warn("No authentication found, API call will be unauthenticated");
        return null;
    }
}
