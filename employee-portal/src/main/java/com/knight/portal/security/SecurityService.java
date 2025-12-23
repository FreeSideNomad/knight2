package com.knight.portal.security;

import com.knight.portal.model.UserInfo;
import com.knight.portal.model.UserInfo.AuthSource;
import com.knight.portal.security.jwt.GatewayTokenHolder;
import com.knight.portal.security.jwt.JwtTokenService;
import com.knight.portal.security.ldap.LdapAuthenticatedUser;
import com.vaadin.flow.component.UI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for accessing current user information from authentication.
 * Supports both LDAP authentication and Gateway (Entra ID) authentication.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityService {

    private final GatewayTokenHolder gatewayTokenHolder;
    private final JwtTokenService jwtTokenService;

    /**
     * Get current authenticated user info.
     * Checks for gateway authentication first, then falls back to LDAP.
     */
    public UserInfo getCurrentUser() {
        // Check if request came through gateway with user info
        if (gatewayTokenHolder.isGatewayRequest()) {
            return createGatewayUserInfo();
        }

        // Fall back to LDAP authentication
        return getLdapUser();
    }

    /**
     * Get user info from LDAP authentication.
     */
    private UserInfo getLdapUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof LdapAuthenticatedUser ldapUser) {
            // Generate JWT claims for display
            Map<String, Object> jwtClaims = jwtTokenService.generateClaimsForUser(ldapUser);

            return new UserInfo(
                    ldapUser.getUsername(),
                    ldapUser.getEmail(),
                    ldapUser.getDisplayName(),
                    ldapUser.getFirstName(),
                    ldapUser.getLastName(),
                    ldapUser.getEmployeeId(),
                    ldapUser.getDepartment(),
                    ldapUser.getAuthorities().stream()
                            .map(GrantedAuthority::getAuthority)
                            .collect(Collectors.toSet()),
                    AuthSource.PORTAL_JWT,
                    jwtClaims
            );
        }
        return null;
    }

    /**
     * Create user info from gateway headers and JWT claims.
     */
    private UserInfo createGatewayUserInfo() {
        Map<String, Object> claims = gatewayTokenHolder.getJwtClaims();

        String email = gatewayTokenHolder.getUserEmail();
        String name = gatewayTokenHolder.getUserName();
        String userId = gatewayTokenHolder.getUserId();

        // Try to get values from JWT claims if not in headers
        if (email == null && claims != null) {
            email = getClaimAsString(claims, "email", "preferred_username", "upn");
        }
        if (name == null && claims != null) {
            name = getClaimAsString(claims, "name");
        }
        if (userId == null && claims != null) {
            userId = getClaimAsString(claims, "oid", "sub");
        }

        // Parse first and last name from display name
        String firstName = null;
        String lastName = null;
        if (name != null && name.contains(" ")) {
            String[] parts = name.split("\\s+", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : null;
        }

        return new UserInfo(
                userId != null ? userId : email,
                email,
                name != null ? name : email,
                firstName,
                lastName,
                null, // No employee ID from Entra
                null, // No department from Entra
                Set.of("ROLE_USER"), // Default role for gateway users
                AuthSource.GATEWAY_ENTRA,
                claims != null ? claims : Map.of()
        );
    }

    /**
     * Get a claim value as string, trying multiple possible claim names.
     */
    private String getClaimAsString(Map<String, Object> claims, String... keys) {
        for (String key : keys) {
            Object value = claims.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Check if user is authenticated.
     */
    public boolean isAuthenticated() {
        // Gateway request with user info is authenticated
        if (gatewayTokenHolder.isGatewayRequest()) {
            return true;
        }

        // Check Spring Security authentication
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }

    /**
     * Logout the current user.
     */
    public void logout() {
        SecurityContextHolder.clearContext();
        UI.getCurrent().getPage().setLocation("/login");
    }
}
