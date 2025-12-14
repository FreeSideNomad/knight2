package com.knight.application.persistence.stubs;

import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow;
import com.knight.domain.approvalworkflows.repository.ApprovalWorkflowRepository;
import org.springframework.stereotype.Repository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of ApprovalWorkflowRepository for development/testing.
 */
@Repository
public class InMemoryApprovalWorkflowRepository implements ApprovalWorkflowRepository {

    private final Map<String, ApprovalWorkflow> store = new ConcurrentHashMap<>();

    @Override
    public void save(ApprovalWorkflow workflow) {
        store.put(workflow.id(), workflow);
    }

    @Override
    public Optional<ApprovalWorkflow> findById(String workflowId) {
        return Optional.ofNullable(store.get(workflowId));
    }
}
