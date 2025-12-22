package com.knight.application.rest.users.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Response after adding a user.
 */
public record AddUserResponse(
    String userId,
    String email,
    String firstName,
    String lastName,
    String status,
    Set<String> roles,
    String passwordResetUrl,
    Instant createdAt
) {}
