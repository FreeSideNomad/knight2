package com.knight.portal.config;

import com.knight.portal.views.LoginView;
import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for LDAP authentication with Vaadin 24.9+.
 * Uses VaadinSecurityConfigurer for Vaadin-specific security settings.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

    private final AuthenticationManager authenticationManager;

    public SecurityConfiguration(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Set the authentication manager for LDAP authentication
        http.authenticationManager(authenticationManager);

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
