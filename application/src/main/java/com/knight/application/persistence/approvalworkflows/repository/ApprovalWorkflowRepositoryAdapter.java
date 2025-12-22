package com.knight.application.persistence.approvalworkflows.repository;

import com.knight.application.persistence.approvalworkflows.entity.ApprovalWorkflowEntity;
import com.knight.application.persistence.approvalworkflows.mapper.ApprovalWorkflowMapper;
import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow;
import com.knight.domain.approvalworkflows.repository.ApprovalWorkflowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
@RequiredArgsConstructor
public class ApprovalWorkflowRepositoryAdapter implements ApprovalWorkflowRepository {

    private final ApprovalWorkflowJpaRepository jpaRepository;
    private final ApprovalWorkflowMapper mapper;

    @Override
    @Transactional
    public void save(ApprovalWorkflow workflow) {
        // Check if entity already exists (for updates with child approvals)
        Optional<ApprovalWorkflowEntity> existing = jpaRepository.findById(UUID.fromString(workflow.id()));
        ApprovalWorkflowEntity entity;
        if (existing.isPresent()) {
            entity = existing.get();
            entity.setStatus(workflow.status().name());
            entity.setCompletedAt(workflow.completedAt());
            // Clear and re-add approvals
            entity.getApprovals().clear();
            for (ApprovalWorkflow.Approval approval : workflow.approvals()) {
                com.knight.application.persistence.approvalworkflows.entity.ApprovalEntity approvalEntity =
                    new com.knight.application.persistence.approvalworkflows.entity.ApprovalEntity();
                approvalEntity.setId(UUID.fromString(approval.approvalId()));
                approvalEntity.setWorkflow(entity);
                approvalEntity.setApproverUserId(approval.approverUserId());
                approvalEntity.setDecision(approval.decision().name());
                approvalEntity.setComments(approval.comments());
                approvalEntity.setApprovedAt(approval.approvedAt());
                entity.getApprovals().add(approvalEntity);
            }
        } else {
            entity = mapper.toEntity(workflow);
        }
        jpaRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ApprovalWorkflow> findById(String workflowId) {
        return jpaRepository.findById(UUID.fromString(workflowId))
            .map(mapper::toDomain);
    }
}
