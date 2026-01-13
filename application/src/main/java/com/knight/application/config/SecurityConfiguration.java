package com.knight.application.config;

import com.knight.application.security.MultiIssuerJwtDecoder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Security Configuration for the Application API.
 *
 * Supports JWT authentication from multiple issuers:
 * - Entra ID (Azure AD) for production via employee-gateway
 * - Employee Portal for local development with LDAP authentication
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtProperties jwtProperties;

    @Bean
    @org.springframework.core.annotation.Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // This filter chain handles everything except /api/login/**
            // which is handled by LoginSecurityConfig with Basic Auth
            .securityMatcher(request -> !request.getRequestURI().startsWith("/api/login"))

            // Disable CSRF for REST API (stateless, token-based auth)
            .csrf(csrf -> csrf.disable())

            // Enable CORS with explicit configuration
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Stateless session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        if (jwtProperties.isEnabled()) {
            log.info("JWT authentication enabled");
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/v3/api-docs.yaml", "/v3/api-docs").permitAll()
                    .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.decoder(multiIssuerJwtDecoder()))
                );
        } else {
            log.warn("JWT authentication DISABLED - all requests permitted");
            http
                .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
                );
        }

        return http.build();
    }

    @Bean
    public JwtDecoder multiIssuerJwtDecoder() {
        Map<String, JwtDecoder> decoders = new HashMap<>();

        // Configure Entra ID decoder
        if (jwtProperties.getEntra().isEnabled() &&
            jwtProperties.getEntra().getTenantId() != null) {

            String entraIssuer = jwtProperties.getEntra().getIssuerUri();
            String entraJwksUri = jwtProperties.getEntra().getJwksUri();

            log.info("Configuring Entra ID JWT decoder - issuer: {}", entraIssuer);
            JwtDecoder entraDecoder = NimbusJwtDecoder.withJwkSetUri(entraJwksUri).build();
            decoders.put(entraIssuer, entraDecoder);
        }

        // Configure Portal decoder
        if (jwtProperties.getPortal().isEnabled()) {
            String portalIssuer = jwtProperties.getPortal().getIssuer();
            String portalJwksUri = jwtProperties.getPortal().getJwksUri();

            log.info("Configuring Portal JWT decoder - issuer: {}, jwks: {}",
                portalIssuer, portalJwksUri);
            JwtDecoder portalDecoder = NimbusJwtDecoder.withJwkSetUri(portalJwksUri).build();
            decoders.put(portalIssuer, portalDecoder);
        }

        // Configure Auth0 decoder
        if (jwtProperties.getAuth0().isEnabled() &&
            jwtProperties.getAuth0().getDomain() != null &&
            !jwtProperties.getAuth0().getDomain().isBlank()) {

            String auth0Issuer = jwtProperties.getAuth0().getIssuerUri();
            String auth0JwksUri = jwtProperties.getAuth0().getJwksUri();

            log.info("Configuring Auth0 JWT decoder - issuer: {}, jwks: {}",
                auth0Issuer, auth0JwksUri);
            JwtDecoder auth0Decoder = NimbusJwtDecoder.withJwkSetUri(auth0JwksUri).build();
            decoders.put(auth0Issuer, auth0Decoder);
        }

        if (decoders.isEmpty()) {
            throw new IllegalStateException("No JWT decoders configured. " +
                "Enable at least one issuer (entra or portal) or disable JWT authentication.");
        }

        log.info("Multi-issuer JWT decoder configured with {} issuer(s): {}",
            decoders.size(), decoders.keySet());

        return new MultiIssuerJwtDecoder(decoders);
    }

    /**
     * CORS configuration that allows requests from trusted origins.
     * This is permissive for internal service-to-service calls.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow all origins for internal services (login gateway proxies requests)
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
