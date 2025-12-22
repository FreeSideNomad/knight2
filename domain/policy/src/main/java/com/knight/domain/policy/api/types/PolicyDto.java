package com.knight.domain.policy.api.types;

import java.time.Instant;

/**
 * Data Transfer Object for PermissionPolicy.
 * Used in API layer to avoid exposing aggregate internals.
 */
public record PolicyDto(
    String id,
    String profileId,
    String subjectUrn,
    String actionPattern,
    String resourcePattern,
    String effect,
    String description,
    boolean systemPolicy,
    Instant createdAt,
    String createdBy,
    Instant updatedAt
) {
    /**
     * Create a summary representation for listing.
     */
    public PolicySummary toSummary() {
        return new PolicySummary(id, subjectUrn, actionPattern, effect, description);
    }

    /**
     * Lightweight summary for lists.
     */
    public record PolicySummary(
        String id,
        String subjectUrn,
        String actionPattern,
        String effect,
        String description
    ) {}
}
