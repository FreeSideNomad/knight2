package com.knight.clientportal.security;

import com.knight.clientportal.model.UserInfo;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Filter that validates JWT tokens from Authorization header.
 * All requests without valid JWT are rejected (except health endpoints).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtValidator jwtValidator;

    public JwtAuthenticationFilter(JwtValidator jwtValidator) {
        this.jwtValidator = jwtValidator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.debug("No Bearer token found for: {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        // Extract headers
        Map<String, String> headers = extractHeaders(request);

        // Validate token
        UserInfo userInfo = jwtValidator.validateAndExtract(token, headers);

        if (userInfo != null) {
            JwtAuthenticationToken authentication = new JwtAuthenticationToken(userInfo, token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("User authenticated: {}", userInfo.email());
        } else {
            log.warn("Invalid JWT token for request: {}", request.getRequestURI());
        }

        filterChain.doFilter(request, response);
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            headers.put(name.toLowerCase(), request.getHeader(name));
        }
        return headers;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Skip filter for actuator endpoints
        return path.startsWith("/actuator");
    }
}
