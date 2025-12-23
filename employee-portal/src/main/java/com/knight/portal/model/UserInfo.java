package com.knight.portal.model;

import java.util.Collection;
import java.util.Set;

/**
 * User information extracted from LDAP authentication.
 */
public record UserInfo(
        String username,
        String email,
        String displayName,
        String firstName,
        String lastName,
        String employeeId,
        String department,
        Collection<String> roles
) {
    /**
     * Get user initials for avatar display.
     */
    public String getInitials() {
        if (firstName != null && !firstName.isBlank() && lastName != null && !lastName.isBlank()) {
            return (firstName.substring(0, 1) + lastName.substring(0, 1)).toUpperCase();
        }
        if (displayName != null && !displayName.isBlank()) {
            String[] parts = displayName.split("\\s+");
            if (parts.length >= 2) {
                return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
            }
            return displayName.substring(0, Math.min(2, displayName.length())).toUpperCase();
        }
        if (username != null && !username.isBlank()) {
            return username.substring(0, Math.min(2, username.length())).toUpperCase();
        }
        return "?";
    }

    /**
     * Get display name with fallback to username or email.
     */
    public String getDisplayName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        if (firstName != null && lastName != null) {
            return firstName + " " + lastName;
        }
        if (username != null && !username.isBlank()) {
            return username;
        }
        return email != null ? email : "Unknown User";
    }

    /**
     * Check if user has a specific role.
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * Check if user has admin role.
     */
    public boolean isAdmin() {
        return hasRole("ROLE_ADMINS");
    }
}
