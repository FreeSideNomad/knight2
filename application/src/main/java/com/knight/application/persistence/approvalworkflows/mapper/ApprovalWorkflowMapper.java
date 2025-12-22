package com.knight.application.persistence.approvalworkflows.mapper;

import com.knight.application.persistence.approvalworkflows.entity.ApprovalEntity;
import com.knight.application.persistence.approvalworkflows.entity.ApprovalWorkflowEntity;
import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class ApprovalWorkflowMapper {

    public ApprovalWorkflowEntity toEntity(ApprovalWorkflow workflow) {
        ApprovalWorkflowEntity entity = new ApprovalWorkflowEntity();
        entity.setId(UUID.fromString(workflow.id()));
        entity.setResourceType(workflow.resourceType());
        entity.setResourceId(workflow.resourceId());
        entity.setRequiredApprovals(workflow.requiredApprovals());
        entity.setStatus(workflow.status().name());
        entity.setInitiatedAt(workflow.initiatedAt());
        entity.setInitiatedBy(workflow.initiatedBy());
        entity.setCompletedAt(workflow.completedAt());

        List<ApprovalEntity> approvalEntities = new ArrayList<>();
        for (ApprovalWorkflow.Approval approval : workflow.approvals()) {
            ApprovalEntity approvalEntity = new ApprovalEntity();
            approvalEntity.setId(UUID.fromString(approval.approvalId()));
            approvalEntity.setWorkflow(entity);
            approvalEntity.setApproverUserId(approval.approverUserId());
            approvalEntity.setDecision(approval.decision().name());
            approvalEntity.setComments(approval.comments());
            approvalEntity.setApprovedAt(approval.approvedAt());
            approvalEntities.add(approvalEntity);
        }
        entity.setApprovals(approvalEntities);

        return entity;
    }

    public ApprovalWorkflow toDomain(ApprovalWorkflowEntity entity) {
        List<ApprovalWorkflow.Approval> approvals = new ArrayList<>();
        for (ApprovalEntity approvalEntity : entity.getApprovals()) {
            ApprovalWorkflow.Approval approval = ApprovalWorkflow.Approval.reconstitute(
                approvalEntity.getId().toString(),
                approvalEntity.getApproverUserId(),
                ApprovalWorkflow.Decision.valueOf(approvalEntity.getDecision()),
                approvalEntity.getComments(),
                approvalEntity.getApprovedAt()
            );
            approvals.add(approval);
        }

        return ApprovalWorkflow.reconstitute(
            entity.getId().toString(),
            entity.getResourceType(),
            entity.getResourceId(),
            entity.getRequiredApprovals(),
            ApprovalWorkflow.Status.valueOf(entity.getStatus()),
            approvals,
            entity.getInitiatedAt(),
            entity.getInitiatedBy(),
            entity.getCompletedAt()
        );
    }
}
