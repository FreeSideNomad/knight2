package com.knight.portal.config;

import com.knight.portal.security.jwt.GatewayAuthenticationFilter;
import com.knight.portal.views.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration for dual authentication:
 * - Gateway pre-authentication (Entra ID via employee-gateway)
 * - LDAP authentication (direct access)
 * Uses VaadinSecurityConfigurer for Vaadin-specific security settings.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final AuthenticationManager authenticationManager;
    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;

    public SecurityConfiguration(AuthenticationManager authenticationManager,
                                  GatewayAuthenticationFilter gatewayAuthenticationFilter) {
        this.authenticationManager = authenticationManager;
        this.gatewayAuthenticationFilter = gatewayAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Set the authentication manager for LDAP authentication
        http.authenticationManager(authenticationManager);

        // Add gateway pre-authentication filter before form login
        http.addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Allow public endpoints without authentication
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/.well-known/**").permitAll()
        );

        // Configure Vaadin security with login view
        http.with(VaadinSecurityConfigurer.vaadin(), configurer -> {
            configurer.loginView(LoginView.class);
        });

        return http.build();
    }
}
