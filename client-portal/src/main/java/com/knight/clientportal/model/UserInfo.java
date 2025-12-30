package com.knight.clientportal.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * User information extracted from JWT token and headers.
 */
public record UserInfo(
    String userId,
    String email,
    String name,
    String picture,
    String ivUser,
    boolean mfaTokenValid,
    boolean hasGuardian,
    Instant tokenIssuedAt,
    Instant tokenExpiresAt,
    String issuer,
    List<String> audience,
    String scope,
    Map<String, Object> customClaims
) {
    public String getDisplayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (email != null && !email.isBlank()) {
            return email.split("@")[0];
        }
        return ivUser != null ? ivUser : "User";
    }

    public String getInitials() {
        String displayName = getDisplayName();
        if (displayName.contains(" ")) {
            String[] parts = displayName.split(" ");
            return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
        }
        return displayName.substring(0, Math.min(2, displayName.length())).toUpperCase();
    }

    public long getTokenLifetimeSeconds() {
        if (tokenIssuedAt != null && tokenExpiresAt != null) {
            return tokenExpiresAt.getEpochSecond() - tokenIssuedAt.getEpochSecond();
        }
        return 0;
    }

    public long getRemainingSeconds() {
        if (tokenExpiresAt != null) {
            return tokenExpiresAt.getEpochSecond() - Instant.now().getEpochSecond();
        }
        return 0;
    }

    public boolean isTokenExpired() {
        return getRemainingSeconds() <= 0;
    }
}
