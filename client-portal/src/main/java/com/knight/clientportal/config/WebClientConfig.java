package com.knight.clientportal.config;

import com.knight.clientportal.security.JwtAuthenticationToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Configuration for WebClient used to communicate with the Platform API.
 */
@Configuration
public class WebClientConfig {

    @Value("${api.base-url}")
    private String apiBaseUrl;

    @Bean
    public WebClient apiWebClient() {
        return WebClient.builder()
                .baseUrl(apiBaseUrl)
                .filter(addAuthorizationHeader())
                .build();
    }

    /**
     * Filter that adds the current user's JWT token to outgoing requests.
     * This allows the platform API to identify the user making the request.
     */
    private ExchangeFilterFunction addAuthorizationHeader() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            var authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                String token = jwtAuth.getRawToken();
                if (token != null && !token.isEmpty()) {
                    ClientRequest authorizedRequest = ClientRequest.from(clientRequest)
                            .header("Authorization", "Bearer " + token)
                            .build();
                    return Mono.just(authorizedRequest);
                }
            }

            return Mono.just(clientRequest);
        });
    }
}
