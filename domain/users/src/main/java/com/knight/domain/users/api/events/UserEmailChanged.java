package com.knight.domain.users.api.events;

import java.time.Instant;

/**
 * Domain event published when a user's email address is changed.
 * This is an audit-relevant event as email changes require re-verification.
 */
public record UserEmailChanged(
    String userId,
    String previousEmail,
    String newEmail,
    String changedBy,
    Instant changedAt
) {}
