package com.knight.domain.serviceprofiles.api.events;

import java.time.Instant;

/**
 * Domain event published when a profile is created.
 */
public record ProfileCreated(
    String profileId,
    String name,
    String primaryClientId,
    String profileType,  // SERVICING or ONLINE
    String status,
    String createdBy,
    Instant createdAt
) {}
