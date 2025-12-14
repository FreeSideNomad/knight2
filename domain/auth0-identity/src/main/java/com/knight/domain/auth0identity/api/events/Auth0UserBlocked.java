package com.knight.domain.auth0identity.api.events;

import java.time.Instant;

/**
 * Domain event published when a user is blocked in Auth0.
 */
public record Auth0UserBlocked(
    String auth0UserId,
    Instant blockedAt
) {}
