package com.knight.application.service.auth0;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(Auth0Properties.class)
public class Auth0HttpClientConfig {

    @Bean
    public RestClient auth0RestClient(Auth0Properties properties) {
        return RestClient.builder()
            .baseUrl("https://" + properties.domain())
            .defaultHeader("Content-Type", "application/json")
            .build();
    }
}
