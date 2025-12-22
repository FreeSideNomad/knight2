package com.knight.domain.approvalworkflows.aggregate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * ApprovalWorkflow aggregate root.
 * Generic approval workflow execution engine.
 */
public class ApprovalWorkflow {

    public enum Status {
        PENDING, APPROVED, REJECTED, EXPIRED
    }

    public enum Decision {
        APPROVE, REJECT
    }

    /**
     * Approval entity within the aggregate.
     */
    public static class Approval {
        private final String approvalId;
        private final String approverUserId;
        private final Decision decision;
        private final String comments;
        private final Instant approvedAt;

        public Approval(String approverUserId, Decision decision, String comments) {
            this.approvalId = UUID.randomUUID().toString();
            this.approverUserId = approverUserId;
            this.decision = decision;
            this.comments = comments;
            this.approvedAt = Instant.now();
        }

        /**
         * Reconstitutes an Approval from persistence.
         */
        public static Approval reconstitute(String approvalId, String approverUserId,
                                            Decision decision, String comments, Instant approvedAt) {
            Approval approval = new Approval(approverUserId, decision, comments);
            try {
                java.lang.reflect.Field idField = Approval.class.getDeclaredField("approvalId");
                idField.setAccessible(true);
                idField.set(approval, approvalId);

                java.lang.reflect.Field approvedAtField = Approval.class.getDeclaredField("approvedAt");
                approvedAtField.setAccessible(true);
                approvedAtField.set(approval, approvedAt);
            } catch (Exception e) {
                throw new RuntimeException("Failed to reconstitute Approval", e);
            }
            return approval;
        }

        public String approvalId() { return approvalId; }
        public String approverUserId() { return approverUserId; }
        public Decision decision() { return decision; }
        public String comments() { return comments; }
        public Instant approvedAt() { return approvedAt; }
    }

    private final String id;
    private final String resourceType;
    private final String resourceId;
    private final int requiredApprovals;
    private Status status;
    private final List<Approval> approvals;
    private final Instant initiatedAt;
    private final String initiatedBy;
    private Instant completedAt;

    private ApprovalWorkflow(String id, String resourceType, String resourceId,
                            int requiredApprovals, String initiatedBy) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.resourceType = Objects.requireNonNull(resourceType, "resourceType cannot be null");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId cannot be null");
        this.requiredApprovals = requiredApprovals;
        this.initiatedBy = Objects.requireNonNull(initiatedBy, "initiatedBy cannot be null");
        this.status = Status.PENDING;
        this.approvals = new ArrayList<>();
        this.initiatedAt = Instant.now();
    }

    public static ApprovalWorkflow initiate(String resourceType, String resourceId,
                                           int requiredApprovals, String initiatedBy) {
        if (resourceType == null || resourceType.isBlank()) {
            throw new IllegalArgumentException("Resource type cannot be null or blank");
        }
        if (resourceId == null || resourceId.isBlank()) {
            throw new IllegalArgumentException("Resource ID cannot be null or blank");
        }
        if (requiredApprovals <= 0) {
            throw new IllegalArgumentException("Required approvals must be greater than 0");
        }
        if (initiatedBy == null || initiatedBy.isBlank()) {
            throw new IllegalArgumentException("Initiated by cannot be null or blank");
        }

        String id = UUID.randomUUID().toString();
        return new ApprovalWorkflow(id, resourceType, resourceId, requiredApprovals, initiatedBy);
    }

    public void recordApproval(String approverUserId, Decision decision, String comments) {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Cannot record approval for workflow in status: " + this.status);
        }
        if (approverUserId == null || approverUserId.isBlank()) {
            throw new IllegalArgumentException("Approver user ID cannot be null or blank");
        }

        // Check if approver already approved
        boolean alreadyApproved = approvals.stream()
            .anyMatch(a -> a.approverUserId().equals(approverUserId));
        if (alreadyApproved) {
            throw new IllegalStateException("User has already provided approval: " + approverUserId);
        }

        Approval approval = new Approval(approverUserId, decision, comments);
        this.approvals.add(approval);

        // If rejected, immediately reject the workflow
        if (decision == Decision.REJECT) {
            this.status = Status.REJECTED;
            this.completedAt = Instant.now();
            return;
        }

        // Check if we have enough approvals
        long approveCount = approvals.stream()
            .filter(a -> a.decision() == Decision.APPROVE)
            .count();

        if (approveCount >= requiredApprovals) {
            this.status = Status.APPROVED;
            this.completedAt = Instant.now();
        }
    }

    public void expire() {
        if (this.status != Status.PENDING) {
            throw new IllegalStateException("Cannot expire workflow in status: " + this.status);
        }
        this.status = Status.EXPIRED;
        this.completedAt = Instant.now();
    }

    /**
     * Reconstitutes an ApprovalWorkflow from persistence.
     */
    public static ApprovalWorkflow reconstitute(String id, String resourceType, String resourceId,
                                                 int requiredApprovals, Status status,
                                                 List<Approval> approvals, Instant initiatedAt,
                                                 String initiatedBy, Instant completedAt) {
        ApprovalWorkflow workflow = new ApprovalWorkflow(id, resourceType, resourceId, requiredApprovals, initiatedBy);
        try {
            java.lang.reflect.Field statusField = ApprovalWorkflow.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(workflow, status);

            java.lang.reflect.Field approvalsField = ApprovalWorkflow.class.getDeclaredField("approvals");
            approvalsField.setAccessible(true);
            List<Approval> approvalList = (List<Approval>) approvalsField.get(workflow);
            approvalList.clear();
            approvalList.addAll(approvals);

            java.lang.reflect.Field initiatedAtField = ApprovalWorkflow.class.getDeclaredField("initiatedAt");
            initiatedAtField.setAccessible(true);
            initiatedAtField.set(workflow, initiatedAt);

            java.lang.reflect.Field completedAtField = ApprovalWorkflow.class.getDeclaredField("completedAt");
            completedAtField.setAccessible(true);
            completedAtField.set(workflow, completedAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstitute ApprovalWorkflow", e);
        }
        return workflow;
    }

    // Getters
    public String id() { return id; }
    public String resourceType() { return resourceType; }
    public String resourceId() { return resourceId; }
    public int requiredApprovals() { return requiredApprovals; }
    public Status status() { return status; }
    public List<Approval> approvals() { return List.copyOf(approvals); }
    public Instant initiatedAt() { return initiatedAt; }
    public String initiatedBy() { return initiatedBy; }
    public Instant completedAt() { return completedAt; }
}
