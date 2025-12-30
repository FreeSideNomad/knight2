package com.knight.indirectportal.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for WebClient to communicate with the Platform API.
 */
@Configuration
@Slf4j
public class ApiClientConfig {

    @Value("${api.base-url}")
    private String apiBaseUrl;

    @Bean
    public WebClient apiWebClient() {
        return WebClient.builder()
                .baseUrl(apiBaseUrl)
                .filter(addAuthTokenFilter())
                .filter(logRequest())
                .build();
    }

    /**
     * Filter to add the JWT token from the current security context to outgoing requests.
     */
    private ExchangeFilterFunction addAuthTokenFilter() {
        return (request, next) -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getCredentials() != null) {
                String token = authentication.getCredentials().toString();
                ClientRequest filtered = ClientRequest.from(request)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .build();
                return next.exchange(filtered);
            }
            return next.exchange(request);
        };
    }

    /**
     * Filter to log outgoing API requests.
     */
    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.debug("API Request: {} {}", clientRequest.method(), clientRequest.url());
            return reactor.core.publisher.Mono.just(clientRequest);
        });
    }
}
