package com.knight.portal.security;

import com.knight.portal.model.UserInfo;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service to access authenticated user information.
 * Delegates to SecurityService for JWT-based authentication.
 */
@Service
@Slf4j
public class AuthenticatedUser {

    private final SecurityService securityService;

    public AuthenticatedUser(SecurityService securityService) {
        this.securityService = securityService;
    }

    /**
     * Get the currently authenticated user info.
     *
     * @return Optional containing the user info, or empty if not authenticated
     */
    public Optional<UserInfo> getUserInfo() {
        return Optional.ofNullable(securityService.getCurrentUser());
    }

    /**
     * Get the display name of the currently authenticated user.
     *
     * @return The display name, or "Guest User" if not authenticated
     */
    public String getDisplayName() {
        return getUserInfo()
                .map(UserInfo::getDisplayName)
                .orElse("Guest User");
    }

    /**
     * Get the email of the currently authenticated user.
     *
     * @return Optional containing the email, or empty if not available
     */
    public Optional<String> getEmail() {
        return getUserInfo()
                .map(UserInfo::email)
                .filter(email -> email != null && !email.isBlank());
    }

    /**
     * Check if the current user is authenticated.
     *
     * @return true if authenticated, false otherwise
     */
    public boolean isAuthenticated() {
        return securityService.isAuthenticated();
    }

    /**
     * Logout the current user by redirecting to the gateway logout endpoint.
     */
    public void logout() {
        UI.getCurrent().getPage().setLocation("/logout");
    }
}
