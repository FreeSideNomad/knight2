package com.knight.domain.approvalworkflows.repository;

import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow;

import java.util.Optional;

/**
 * Repository interface for ApprovalWorkflow aggregate persistence.
 * Implementations handle the actual storage mechanism (JPA, in-memory, etc.)
 */
public interface ApprovalWorkflowRepository {

    /**
     * Persists an ApprovalWorkflow aggregate.
     *
     * @param workflow the approval workflow to save
     */
    void save(ApprovalWorkflow workflow);

    /**
     * Retrieves an ApprovalWorkflow by its identifier.
     *
     * @param workflowId the workflow identifier
     * @return the approval workflow if found
     */
    Optional<ApprovalWorkflow> findById(String workflowId);
}
