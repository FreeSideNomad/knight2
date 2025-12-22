package com.knight.domain.auth0identity.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for OAuth2 token request.
 */
public record Auth0TokenResponse(
    @JsonProperty("access_token") String accessToken,
    @JsonProperty("expires_in") long expiresIn,
    @JsonProperty("token_type") String tokenType,
    String scope
) {}
