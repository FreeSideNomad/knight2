package com.knight.application.security.auth0;

import com.knight.application.config.JwtProperties;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Filter that populates Auth0UserContext for Auth0 authenticated requests.
 * Runs after JWT authentication but before controller execution.
 *
 * Loads:
 * - JWT claims (sub, iss, scope, azp)
 * - User by identity_provider_user_id (JWT sub claim)
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(10)  // Run after Spring Security filters
public class Auth0UserContextFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;
    private final Auth0UserContext auth0UserContext;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            populateAuth0Context();
        } catch (Exception e) {
            log.warn("Failed to populate Auth0 user context: {}", e.getMessage());
            // Continue processing - context will be empty but request continues
        }

        filterChain.doFilter(request, response);
    }

    private void populateAuth0Context() {
        if (!jwtProperties.getAuth0().isEnabled()) {
            return;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return;
        }

        Jwt jwt = jwtAuth.getToken();
        if (jwt.getIssuer() == null) {
            return;
        }

        String issuer = jwt.getIssuer().toString();

        // Check if this is an Auth0 token
        String auth0Issuer = jwtProperties.getAuth0().getIssuerUri();
        if (!issuer.equals(auth0Issuer)) {
            return;
        }

        log.debug("Processing Auth0 authenticated request for subject: {}", jwt.getSubject());

        // Extract claims
        String subject = jwt.getSubject();  // auth0|694aa9789daa0cb005839f68
        String scopeString = jwt.getClaimAsString("scope");
        List<String> scopes = scopeString != null
            ? Arrays.asList(scopeString.split(" "))
            : Collections.emptyList();
        String authorizedParty = jwt.getClaimAsString("azp");

        // Load user by identity_provider_user_id
        Optional<User> userOpt = userRepository.findByIdentityProviderUserId(subject);

        if (userOpt.isEmpty()) {
            log.warn("No user found for Auth0 subject: {}", subject);
            auth0UserContext.initialize(subject, issuer, scopes, authorizedParty, null);
            return;
        }

        User user = userOpt.get();
        log.debug("Found user: {} for Auth0 subject", user.email());

        // Initialize context with JWT claims and user
        auth0UserContext.initialize(subject, issuer, scopes, authorizedParty, user);

        log.debug("Auth0 user context initialized - User: {}, Email: {}, Profile: {}",
            user.id().id(),
            user.email(),
            user.profileId().urn()
        );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip for actuator, health checks, etc.
        String path = request.getRequestURI();
        return path.startsWith("/actuator") || path.equals("/health");
    }
}
