package com.knight.application.security.login;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for /api/login/* endpoints.
 * Uses Basic Auth to protect login API endpoints from direct access.
 * Only the client-login gateway should have these credentials.
 */
@Configuration
public class LoginSecurityConfig {

    @Value("${login.basic-auth.username:gateway}")
    private String username;

    @Value("${login.basic-auth.password:changeme}")
    private String password;

    /**
     * Security filter chain for /api/login/** endpoints.
     * Order 1 ensures this takes precedence over JWT security.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain loginSecurityFilterChain(HttpSecurity http) throws Exception {
        // Create a dedicated AuthenticationManager for this filter chain
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(loginUserDetailsService());
        authProvider.setPasswordEncoder(NoOpPasswordEncoder.getInstance());
        AuthenticationManager authManager = new ProviderManager(authProvider);

        http
            .securityMatcher("/api/login/**")
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(loginCorsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated())
            .authenticationManager(authManager)
            .httpBasic(basic -> {});

        return http.build();
    }

    /**
     * CORS configuration for login endpoints.
     * Permissive since requests come through nginx gateway.
     */
    @Bean
    public CorsConfigurationSource loginCorsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/login/**", configuration);
        return source;
    }

    /**
     * In-memory user details service for Basic Auth.
     */
    private UserDetailsService loginUserDetailsService() {
        UserDetails gatewayUser = User.builder()
            .username(username)
            .password(password)  // NoOpPasswordEncoder handles plain text
            .roles("GATEWAY")
            .build();
        return new InMemoryUserDetailsManager(gatewayUser);
    }
}
