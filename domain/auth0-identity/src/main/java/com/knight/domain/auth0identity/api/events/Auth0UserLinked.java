package com.knight.domain.auth0identity.api.events;

import java.time.Instant;

/**
 * Domain event published when an Auth0 user is linked to an internal user.
 */
public record Auth0UserLinked(
    String auth0UserId,
    String internalUserId,
    Instant linkedAt
) {}
