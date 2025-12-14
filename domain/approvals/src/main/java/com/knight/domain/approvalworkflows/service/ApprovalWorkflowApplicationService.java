package com.knight.domain.approvalworkflows.service;

import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow;
import com.knight.domain.approvalworkflows.api.commands.ApprovalWorkflowCommands;
import com.knight.domain.approvalworkflows.api.events.WorkflowInitiated;
import com.knight.domain.approvalworkflows.api.queries.ApprovalWorkflowQueries;
import com.knight.domain.approvalworkflows.repository.ApprovalWorkflowRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Application service for Approval Workflow Engine.
 * Orchestrates approval workflow operations with transactions and event publishing.
 */
@Service
public class ApprovalWorkflowApplicationService implements ApprovalWorkflowCommands, ApprovalWorkflowQueries {

    private final ApprovalWorkflowRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public ApprovalWorkflowApplicationService(
        ApprovalWorkflowRepository repository,
        ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public String initiateWorkflow(InitiateWorkflowCmd cmd) {
        ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
            cmd.resourceType(),
            cmd.resourceId(),
            cmd.requiredApprovals(),
            cmd.initiatedBy()
        );

        repository.save(workflow);

        eventPublisher.publishEvent(new WorkflowInitiated(
            workflow.id(),
            cmd.resourceType(),
            cmd.resourceId(),
            cmd.requiredApprovals(),
            Instant.now()
        ));

        return workflow.id();
    }

    @Override
    @Transactional
    public void recordApproval(RecordApprovalCmd cmd) {
        ApprovalWorkflow workflow = repository.findById(cmd.workflowId())
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + cmd.workflowId()));

        ApprovalWorkflow.Decision decision = ApprovalWorkflow.Decision.valueOf(cmd.decision());

        workflow.recordApproval(cmd.approverUserId(), decision, cmd.comments());

        repository.save(workflow);
    }

    @Override
    @Transactional
    public void expireWorkflow(ExpireWorkflowCmd cmd) {
        ApprovalWorkflow workflow = repository.findById(cmd.workflowId())
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + cmd.workflowId()));

        workflow.expire();

        repository.save(workflow);
    }

    @Override
    public ApprovalWorkflowSummary getWorkflowSummary(String workflowId) {
        ApprovalWorkflow workflow = repository.findById(workflowId)
            .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

        long approveCount = workflow.approvals().stream()
            .filter(a -> a.decision() == ApprovalWorkflow.Decision.APPROVE)
            .count();

        return new ApprovalWorkflowSummary(
            workflow.id(),
            workflow.status().name(),
            workflow.resourceType(),
            workflow.resourceId(),
            workflow.requiredApprovals(),
            (int) approveCount
        );
    }
}
