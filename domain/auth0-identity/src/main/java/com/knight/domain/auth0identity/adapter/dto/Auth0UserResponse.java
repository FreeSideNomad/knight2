package com.knight.domain.auth0identity.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Response DTO for Auth0 user data.
 */
public record Auth0UserResponse(
    @JsonProperty("user_id") String userId,
    String email,
    String name,
    @JsonProperty("given_name") String givenName,
    @JsonProperty("family_name") String familyName,
    @JsonProperty("email_verified") boolean emailVerifiedStatus,
    boolean blocked,
    String picture,
    @JsonProperty("last_login") String lastLogin,
    @JsonProperty("app_metadata") Map<String, Object> appMetadata,
    @JsonProperty("user_metadata") Map<String, Object> userMetadata
) {}
