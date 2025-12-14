package com.knight.domain.approvalworkflows.api.events;

import java.time.Instant;

/**
 * Domain event published when an approval workflow is initiated.
 */
public record WorkflowInitiated(
    String workflowId,
    String resourceType,
    String resourceId,
    int requiredApprovals,
    Instant initiatedAt
) {}
