package com.knight.portal.security.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Pre-authentication filter for requests coming through the employee-gateway.
 * When the gateway forwards a request with Authorization header and user info headers,
 * this filter creates a Spring Security authentication without requiring LDAP login.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-Auth-User-Id";
    private static final String HEADER_USER_EMAIL = "X-Auth-User-Email";
    private static final String HEADER_USER_NAME = "X-Auth-User-Name";

    private final GatewayTokenHolder gatewayTokenHolder;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // Only process if not already authenticated
        if (SecurityContextHolder.getContext().getAuthentication() == null ||
            !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {

            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            String userEmail = request.getHeader(HEADER_USER_EMAIL);
            String userName = request.getHeader(HEADER_USER_NAME);
            String userId = request.getHeader(HEADER_USER_ID);

            // Check if this is a gateway-authenticated request
            if (authHeader != null && authHeader.startsWith("Bearer ") && userEmail != null) {
                String token = authHeader.substring(7);

                // Store token and user info for later use
                gatewayTokenHolder.setToken(token);
                gatewayTokenHolder.setUserEmail(userEmail);
                gatewayTokenHolder.setUserName(userName);
                gatewayTokenHolder.setUserId(userId);

                // Create authentication with user info
                String principal = userEmail;
                List<SimpleGrantedAuthority> authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_USER"),
                    new SimpleGrantedAuthority("ROLE_GATEWAY_USER")
                );

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, token, authorities);

                // Set additional details
                authentication.setDetails(new GatewayAuthenticationDetails(userId, userEmail, userName));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Gateway authentication successful for user: {}", userEmail);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Details object to hold gateway user information.
     */
    public record GatewayAuthenticationDetails(String userId, String email, String name) {}
}
