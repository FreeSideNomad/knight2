package com.knight.domain.approvalworkflows.api.queries;

/**
 * Query interface for Approval Workflow Management.
 */
public interface ApprovalWorkflowQueries {

    record ApprovalWorkflowSummary(
        String workflowId,
        String status,
        String resourceType,
        String resourceId,
        int requiredApprovals,
        int receivedApprovals
    ) {}

    ApprovalWorkflowSummary getWorkflowSummary(String workflowId);
}
