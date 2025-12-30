package com.knight.indirectportal.security;

import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filter that authenticates requests using Auth0 JWT tokens passed from the client-login gateway.
 * Expects the JWT to be in the Authorization header as "Bearer {token}".
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class Auth0AuthenticationFilter extends OncePerRequestFilter {

    private final Auth0JwtValidator jwtValidator;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                DecodedJWT jwt = jwtValidator.validateToken(token);

                // Extract user information - prefer gateway headers over JWT claims
                String email = request.getHeader("X-Auth-User-Email");
                if (email == null || email.isEmpty()) {
                    // Fallback to JWT claim (try standard and namespaced)
                    email = jwt.getClaim("email").asString();
                    if (email == null) {
                        email = jwt.getClaim("https://auth-gateway.local/email").asString();
                    }
                }

                String userId = request.getHeader("X-Auth-User-Id");
                if (userId == null || userId.isEmpty()) {
                    userId = jwt.getSubject();
                }

                String name = request.getHeader("X-Auth-User-Name");

                // Create authentication object with token as credentials for forwarding to backend
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        new Auth0User(userId, email, jwt, name),
                        token,  // Store token as credentials for WebClient to forward
                        List.of(new SimpleGrantedAuthority("ROLE_INDIRECT_CLIENT"))
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated indirect client user: {}", email);

            } catch (Exception e) {
                log.error("JWT validation failed", e);
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }
}
