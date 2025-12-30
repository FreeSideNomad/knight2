package com.knight.indirectportal.config;

import com.knight.indirectportal.security.Auth0AuthenticationFilter;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for Auth0 JWT-based authentication.
 * Uses VaadinWebSecurity for Vaadin-specific security settings.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration extends VaadinWebSecurity {

    private final Auth0AuthenticationFilter auth0AuthenticationFilter;

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Add Auth0 JWT authentication filter
        http.addFilterBefore(auth0AuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Allow public endpoints without authentication
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
        );

        // Configure Vaadin security
        super.configure(http);
    }
}
