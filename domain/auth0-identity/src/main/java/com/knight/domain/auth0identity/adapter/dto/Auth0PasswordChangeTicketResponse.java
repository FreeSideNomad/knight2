package com.knight.domain.auth0identity.adapter.dto;

/**
 * Response DTO for password change ticket creation.
 */
public record Auth0PasswordChangeTicketResponse(
    String ticket
) {}
