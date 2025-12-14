package com.knight.application.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security Configuration
 *
 * MVP Configuration: Permits all requests without authentication.
 * This is a minimal setup for the MVP phase.
 *
 * Future enhancement: Will be configured with OAuth2 Resource Server
 * for Auth0 integration when moving beyond MVP.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for REST API (stateless, token-based auth planned)
            .csrf(csrf -> csrf.disable())

            // Enable CORS with default configuration (uses WebMvcConfigurer)
            .cors(Customizer.withDefaults())

            // MVP: Allow all requests without authentication
            // TODO: Replace with OAuth2 resource server configuration for production
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().permitAll()  // MVP: permit all
            );

        return http.build();
    }
}
