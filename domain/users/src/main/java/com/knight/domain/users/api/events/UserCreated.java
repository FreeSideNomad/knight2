package com.knight.domain.users.api.events;

import java.time.Instant;

/**
 * Domain event published when a user is created.
 */
public record UserCreated(
    String userId,
    String email,
    String userType,
    String identityProvider,
    Instant createdAt
) {}
