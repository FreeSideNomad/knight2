package com.knight.domain.auth0identity.api;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Auth0 Token Service interface.
 * Handles token validation and management for Auth0.
 */
public interface Auth0TokenService {

    /**
     * Validates an access token.
     *
     * @param accessToken the access token to validate
     * @return the token info if valid
     */
    Optional<TokenInfo> validateToken(String accessToken);

    /**
     * Retrieves the Management API token for server-to-server operations.
     *
     * @return the management API token
     */
    String getManagementApiToken();

    /**
     * Revokes all tokens for a user.
     *
     * @param auth0UserId the Auth0 user ID
     */
    void revokeUserTokens(String auth0UserId);

    // Response records

    record TokenInfo(
        String subject,
        String audience,
        Set<String> scopes,
        Instant expiresAt,
        Instant issuedAt
    ) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
