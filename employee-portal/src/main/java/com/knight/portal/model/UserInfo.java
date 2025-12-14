package com.knight.portal.model;

import java.time.Instant;
import java.util.Map;

/**
 * User information extracted from JWT token and headers
 */
public record UserInfo(
        String userId,
        String email,
        String name,
        String preferredUsername,
        Instant tokenIssuedAt,
        Instant tokenExpiresAt,
        String issuer,
        String audience,
        String scope,
        Map<String, Object> customClaims
) {
    /**
     * Get display name with fallback to email
     */
    public String getDisplayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (preferredUsername != null && !preferredUsername.isBlank()) {
            return preferredUsername;
        }
        return email != null ? email : "Unknown User";
    }

    /**
     * Get user initials for avatar
     */
    public String getInitials() {
        String displayName = getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            return "?";
        }

        String[] parts = displayName.split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return displayName.substring(0, Math.min(2, displayName.length())).toUpperCase();
    }

    /**
     * Get token lifetime in seconds
     */
    public long getTokenLifetimeSeconds() {
        if (tokenIssuedAt != null && tokenExpiresAt != null) {
            return tokenExpiresAt.getEpochSecond() - tokenIssuedAt.getEpochSecond();
        }
        return 0;
    }

    /**
     * Get remaining token time in seconds
     */
    public long getRemainingSeconds() {
        if (tokenExpiresAt != null) {
            return tokenExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        }
        return 0;
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired() {
        return getRemainingSeconds() <= 0;
    }
}
