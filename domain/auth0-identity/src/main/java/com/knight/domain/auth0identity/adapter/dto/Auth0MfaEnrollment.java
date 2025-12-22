package com.knight.domain.auth0identity.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response DTO for MFA enrollment data from Auth0.
 */
public record Auth0MfaEnrollment(
    String id,
    String status,
    String type,
    @JsonProperty("enrolled_at") String enrolledAt
) {}
