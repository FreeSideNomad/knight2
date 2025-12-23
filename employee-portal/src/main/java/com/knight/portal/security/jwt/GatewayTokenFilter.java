package com.knight.portal.security.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Filter to detect and capture Bearer tokens and user info from employee-gateway.
 * When running behind the gateway, Entra ID tokens and user headers are forwarded
 * and should be captured for display and pass-through to API calls.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-Auth-User-Id";
    private static final String HEADER_USER_EMAIL = "X-Auth-User-Email";
    private static final String HEADER_USER_NAME = "X-Auth-User-Name";

    private final GatewayTokenHolder gatewayTokenHolder;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Capture Authorization header (JWT token)
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            gatewayTokenHolder.setToken(token);

            // Parse JWT claims from token payload
            Map<String, Object> claims = parseJwtClaims(token);
            if (claims != null) {
                gatewayTokenHolder.setJwtClaims(claims);
            }

            log.debug("Captured gateway token for pass-through");
        }

        // Capture gateway user headers
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId != null && !userId.isBlank()) {
            gatewayTokenHolder.setUserId(userId);
        }

        String userEmail = request.getHeader(HEADER_USER_EMAIL);
        if (userEmail != null && !userEmail.isBlank()) {
            gatewayTokenHolder.setUserEmail(userEmail);
        }

        String userName = request.getHeader(HEADER_USER_NAME);
        if (userName != null && !userName.isBlank()) {
            gatewayTokenHolder.setUserName(userName);
        }

        if (gatewayTokenHolder.isGatewayRequest()) {
            log.debug("Gateway request detected - user: {}, email: {}",
                    gatewayTokenHolder.getUserName(),
                    gatewayTokenHolder.getUserEmail());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Parse JWT claims from the payload section (without signature verification).
     * This is safe because we're only using it for display purposes.
     */
    private Map<String, Object> parseJwtClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Invalid JWT format: expected 3 parts, got {}", parts.length);
                return null;
            }

            // Decode the payload (second part)
            String payload = new String(
                    Base64.getUrlDecoder().decode(parts[1]),
                    StandardCharsets.UTF_8
            );

            return objectMapper.readValue(payload, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JWT claims: {}", e.getMessage());
            return null;
        }
    }
}
