package com.knight.domain.approvalworkflows.api.commands;

/**
 * Command interface for Approval Workflow Management.
 * Defines contract for initiating and managing approval workflows.
 */
public interface ApprovalWorkflowCommands {

    String initiateWorkflow(InitiateWorkflowCmd cmd);

    record InitiateWorkflowCmd(
        String resourceType,
        String resourceId,
        int requiredApprovals,
        String initiatedBy
    ) {}

    void recordApproval(RecordApprovalCmd cmd);

    record RecordApprovalCmd(
        String workflowId,
        String approverUserId,
        String decision,
        String comments
    ) {}

    void expireWorkflow(ExpireWorkflowCmd cmd);

    record ExpireWorkflowCmd(String workflowId) {}
}
