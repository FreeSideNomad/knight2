package com.knight.clientportal.security;

import com.knight.clientportal.model.UserInfo;
import com.vaadin.flow.server.VaadinRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service to access current user information from security context.
 */
@Service
public class SecurityService {

    /**
     * Get the current authenticated user's info.
     */
    public Optional<UserInfo> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getUserInfo());
        }
        return Optional.empty();
    }

    /**
     * Get the raw JWT token from current authentication.
     */
    public Optional<String> getCurrentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return Optional.of(jwtAuth.getRawToken());
        }
        return Optional.empty();
    }

    /**
     * Check if user is authenticated.
     */
    public boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth instanceof JwtAuthenticationToken;
    }

    /**
     * Get header value from current request.
     */
    public String getHeader(String name) {
        VaadinRequest request = VaadinRequest.getCurrent();
        if (request != null) {
            return request.getHeader(name);
        }
        return null;
    }
}
