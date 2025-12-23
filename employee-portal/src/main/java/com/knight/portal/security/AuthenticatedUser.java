package com.knight.portal.security;

import com.knight.portal.model.UserInfo;
import com.knight.portal.model.UserInfo.AuthSource;
import com.vaadin.flow.component.UI;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * Service to access authenticated user information.
 * Delegates to SecurityService for LDAP and Gateway authentication.
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
     * Get the authentication source for the current user.
     *
     * @return the auth source, or LDAP as default
     */
    public AuthSource getAuthSource() {
        return getUserInfo()
                .map(UserInfo::authSource)
                .orElse(AuthSource.LDAP);
    }

    /**
     * Get JWT claims for the current user.
     * For LDAP users, these are the claims that would be generated.
     * For Gateway users, these are the actual claims from the Entra token.
     *
     * @return map of JWT claims
     */
    public Map<String, Object> getJwtClaims() {
        return getUserInfo()
                .map(UserInfo::jwtClaims)
                .orElse(Map.of());
    }

    /**
     * Logout the current user.
     */
    public void logout() {
        UI.getCurrent().getPage().setLocation("/logout");
    }
}
