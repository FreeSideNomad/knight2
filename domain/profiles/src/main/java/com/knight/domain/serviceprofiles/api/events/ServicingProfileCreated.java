package com.knight.domain.serviceprofiles.api.events;

import java.time.Instant;

/**
 * Domain event published when a servicing profile is created.
 */
public record ServicingProfileCreated(
    String profileId,
    String clientId,
    String status,
    String createdBy,
    Instant createdAt
) {}
