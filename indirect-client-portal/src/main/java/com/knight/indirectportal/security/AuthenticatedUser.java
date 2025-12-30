package com.knight.indirectportal.security;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.spring.security.AuthenticationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Service to access the currently authenticated user.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedUser {

    private final AuthenticationContext authenticationContext;

    /**
     * Get the currently authenticated Auth0 user.
     */
    public Optional<Auth0User> get() {
        return authenticationContext.getAuthenticatedUser(Auth0User.class);
    }

    /**
     * Get the display name of the current user.
     */
    public String getDisplayName() {
        return get().map(Auth0User::getName).orElse("Unknown User");
    }

    /**
     * Get the email of the current user.
     */
    public String getEmail() {
        return get().map(Auth0User::getEmail).orElse("");
    }

    /**
     * Get the user ID of the current user.
     */
    public String getUserId() {
        return get().map(Auth0User::getUserId).orElse("");
    }

    /**
     * Logout the current user by redirecting to the gateway logout endpoint.
     */
    public void logout() {
        UI.getCurrent().getPage().setLocation("/logout");
    }
}
