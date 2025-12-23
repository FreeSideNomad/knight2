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

import java.util.HashMap;
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF for REST API (stateless, token-based auth)
            .csrf(csrf -> csrf.disable())

            // Enable CORS with default configuration
            .cors(Customizer.withDefaults())

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

        if (decoders.isEmpty()) {
            throw new IllegalStateException("No JWT decoders configured. " +
                "Enable at least one issuer (entra or portal) or disable JWT authentication.");
        }

        log.info("Multi-issuer JWT decoder configured with {} issuer(s): {}",
            decoders.size(), decoders.keySet());

        return new MultiIssuerJwtDecoder(decoders);
    }
}
