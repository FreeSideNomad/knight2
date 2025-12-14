package com.knight.domain.policy.api.events;

import java.time.Instant;

/**
 * Domain event published when a policy is created.
 */
public record PolicyCreated(
    String policyId,
    String policyType,
    String subject,
    Instant createdAt
) {}
