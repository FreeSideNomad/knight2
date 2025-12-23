package com.knight.portal.security;

import com.knight.portal.model.UserInfo;
import com.knight.portal.security.ldap.LdapAuthenticatedUser;
import com.vaadin.flow.component.UI;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * Service for accessing current user information from LDAP authentication.
 */
@Service
public class SecurityService {

    /**
     * Get current authenticated user info.
     */
    public UserInfo getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.getPrincipal() instanceof LdapAuthenticatedUser ldapUser) {
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
                            .collect(Collectors.toSet())
            );
        }
        return null;
    }

    /**
     * Check if user is authenticated.
     */
    public boolean isAuthenticated() {
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
