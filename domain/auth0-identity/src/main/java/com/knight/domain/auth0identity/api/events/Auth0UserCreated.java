package com.knight.domain.auth0identity.api.events;

import java.time.Instant;

/**
 * Domain event published when a user is created in Auth0.
 */
public record Auth0UserCreated(
    String auth0UserId,
    String email,
    String name,
    Instant createdAt
) {}
