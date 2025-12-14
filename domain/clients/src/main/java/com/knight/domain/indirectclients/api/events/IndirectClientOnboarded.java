package com.knight.domain.indirectclients.api.events;

import java.time.Instant;

/**
 * Domain event published when an indirect client is onboarded.
 */
public record IndirectClientOnboarded(
    String indirectClientId,
    String parentClientId,
    String businessName,
    Instant onboardedAt
) {}
