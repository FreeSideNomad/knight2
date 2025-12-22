package com.knight.domain.auth0identity.adapter.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request DTO for creating a password change ticket in Auth0.
 */
public record Auth0PasswordChangeTicketRequest(
    @JsonProperty("user_id") String userId,
    @JsonProperty("result_url") String resultUrl,
    @JsonProperty("ttl_sec") int ttlSec,
    @JsonProperty("mark_email_as_verified") boolean markEmailAsVerified
) {}
