package com.knight.domain.policy.aggregate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Policy aggregate root.
 * Manages permission and approval policies.
 */
public class Policy {

    public enum PolicyType {
        PERMISSION, APPROVAL
    }

    private final String id;
    private final PolicyType policyType;
    private final String subject;
    private String action;
    private String resource;
    private Integer approverCount;
    private final Instant createdAt;
    private Instant updatedAt;

    private Policy(String id, PolicyType policyType, String subject,
                  String action, String resource, Integer approverCount) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.policyType = Objects.requireNonNull(policyType, "policyType cannot be null");
        this.subject = Objects.requireNonNull(subject, "subject cannot be null");
        this.action = action;
        this.resource = resource;
        this.approverCount = approverCount;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static Policy create(PolicyType policyType, String subject,
                               String action, String resource, Integer approverCount) {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject cannot be null or blank");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Action cannot be null or blank");
        }
        if (resource == null || resource.isBlank()) {
            throw new IllegalArgumentException("Resource cannot be null or blank");
        }

        // Approval policies require approver count
        if (policyType == PolicyType.APPROVAL) {
            if (approverCount == null || approverCount <= 0) {
                throw new IllegalArgumentException("Approval policy requires approverCount > 0");
            }
        }

        String id = UUID.randomUUID().toString();
        return new Policy(id, policyType, subject, action, resource, approverCount);
    }

    public void update(String action, String resource, Integer approverCount) {
        if (action != null && !action.isBlank()) {
            this.action = action;
        }
        if (resource != null && !resource.isBlank()) {
            this.resource = resource;
        }

        // For approval policies, validate approverCount
        if (this.policyType == PolicyType.APPROVAL && approverCount != null) {
            if (approverCount <= 0) {
                throw new IllegalArgumentException("Approval policy requires approverCount > 0");
            }
            this.approverCount = approverCount;
        }

        this.updatedAt = Instant.now();
    }

    /**
     * Reconstitutes a Policy from persistence.
     */
    public static Policy reconstitute(String id, PolicyType policyType, String subject,
                                       String action, String resource, Integer approverCount,
                                       Instant createdAt, Instant updatedAt) {
        Policy policy = new Policy(id, policyType, subject, action, resource, approverCount);
        // Use reflection or package-private setter to override timestamps
        try {
            java.lang.reflect.Field createdAtField = Policy.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(policy, createdAt);

            java.lang.reflect.Field updatedAtField = Policy.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(policy, updatedAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to reconstitute Policy", e);
        }
        return policy;
    }

    // Getters
    public String id() { return id; }
    public PolicyType policyType() { return policyType; }
    public String subject() { return subject; }
    public String action() { return action; }
    public String resource() { return resource; }
    public Integer approverCount() { return approverCount; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
