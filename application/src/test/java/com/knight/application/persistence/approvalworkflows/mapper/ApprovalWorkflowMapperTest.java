package com.knight.application.persistence.approvalworkflows.mapper;

import com.knight.application.persistence.approvalworkflows.entity.ApprovalEntity;
import com.knight.application.persistence.approvalworkflows.entity.ApprovalWorkflowEntity;
import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ApprovalWorkflowMapper.
 */
class ApprovalWorkflowMapperTest {

    private ApprovalWorkflowMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ApprovalWorkflowMapper();
    }

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map workflow to entity")
        void shouldMapWorkflowToEntity() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "EXPENSE",
                "expense-12345",
                2,
                "user:john"
            );

            ApprovalWorkflowEntity entity = mapper.toEntity(workflow);

            assertThat(entity.getId()).isEqualTo(UUID.fromString(workflow.id()));
            assertThat(entity.getResourceType()).isEqualTo("EXPENSE");
            assertThat(entity.getResourceId()).isEqualTo("expense-12345");
            assertThat(entity.getRequiredApprovals()).isEqualTo(2);
            assertThat(entity.getStatus()).isEqualTo("PENDING");
            assertThat(entity.getInitiatedBy()).isEqualTo("user:john");
            assertThat(entity.getInitiatedAt()).isNotNull();
            assertThat(entity.getCompletedAt()).isNull();
            assertThat(entity.getApprovals()).isEmpty();
        }

        @Test
        @DisplayName("should map workflow with approvals to entity")
        void shouldMapWorkflowWithApprovalsToEntity() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "PURCHASE_ORDER",
                "po-67890",
                3,
                "user:alice"
            );
            workflow.recordApproval("user:bob", ApprovalWorkflow.Decision.APPROVE, "Looks good");
            workflow.recordApproval("user:carol", ApprovalWorkflow.Decision.APPROVE, "Approved");

            ApprovalWorkflowEntity entity = mapper.toEntity(workflow);

            assertThat(entity.getApprovals()).hasSize(2);
            assertThat(entity.getStatus()).isEqualTo("PENDING"); // Still pending (needs 3)
        }

        @Test
        @DisplayName("should map completed workflow to entity")
        void shouldMapCompletedWorkflowToEntity() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "CONTRACT",
                "contract-111",
                2,
                "user:initiator"
            );
            workflow.recordApproval("user:approver1", ApprovalWorkflow.Decision.APPROVE, null);
            workflow.recordApproval("user:approver2", ApprovalWorkflow.Decision.APPROVE, "Agreed");

            ApprovalWorkflowEntity entity = mapper.toEntity(workflow);

            assertThat(entity.getStatus()).isEqualTo("APPROVED");
            assertThat(entity.getCompletedAt()).isNotNull();
            assertThat(entity.getApprovals()).hasSize(2);
        }

        @Test
        @DisplayName("should map rejected workflow to entity")
        void shouldMapRejectedWorkflowToEntity() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                "BUDGET",
                "budget-222",
                2,
                "user:requester"
            );
            workflow.recordApproval("user:reviewer", ApprovalWorkflow.Decision.REJECT, "Over budget");

            ApprovalWorkflowEntity entity = mapper.toEntity(workflow);

            assertThat(entity.getStatus()).isEqualTo("REJECTED");
            assertThat(entity.getCompletedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Entity to Domain Mapping")
    class EntityToDomainTests {

        @Test
        @DisplayName("should map entity to pending workflow")
        void shouldMapEntityToPendingWorkflow() {
            ApprovalWorkflowEntity entity = createWorkflowEntity(
                "PROJECT", "proj-123", 2, "PENDING", "user:pm", null
            );

            ApprovalWorkflow workflow = mapper.toDomain(entity);

            assertThat(workflow.id()).isEqualTo(entity.getId().toString());
            assertThat(workflow.resourceType()).isEqualTo("PROJECT");
            assertThat(workflow.resourceId()).isEqualTo("proj-123");
            assertThat(workflow.requiredApprovals()).isEqualTo(2);
            assertThat(workflow.status()).isEqualTo(ApprovalWorkflow.Status.PENDING);
            assertThat(workflow.initiatedBy()).isEqualTo("user:pm");
            assertThat(workflow.completedAt()).isNull();
            assertThat(workflow.approvals()).isEmpty();
        }

        @Test
        @DisplayName("should map entity with approvals to workflow")
        void shouldMapEntityWithApprovalsToWorkflow() {
            ApprovalWorkflowEntity entity = createWorkflowEntity(
                "INVOICE", "inv-456", 2, "PENDING", "user:submitter", null
            );

            // Add approvals
            ApprovalEntity approval1 = new ApprovalEntity();
            approval1.setId(UUID.randomUUID());
            approval1.setWorkflow(entity);
            approval1.setApproverUserId("user:manager");
            approval1.setDecision("APPROVE");
            approval1.setComments("Verified");
            approval1.setApprovedAt(Instant.now());
            entity.getApprovals().add(approval1);

            ApprovalWorkflow workflow = mapper.toDomain(entity);

            assertThat(workflow.approvals()).hasSize(1);
            ApprovalWorkflow.Approval approval = workflow.approvals().get(0);
            assertThat(approval.approverUserId()).isEqualTo("user:manager");
            assertThat(approval.decision()).isEqualTo(ApprovalWorkflow.Decision.APPROVE);
            assertThat(approval.comments()).isEqualTo("Verified");
        }

        @Test
        @DisplayName("should map approved entity to workflow")
        void shouldMapApprovedEntityToWorkflow() {
            Instant completedAt = Instant.now();
            ApprovalWorkflowEntity entity = createWorkflowEntity(
                "PAYMENT", "pay-789", 1, "APPROVED", "user:payer", completedAt
            );

            ApprovalEntity approval = new ApprovalEntity();
            approval.setId(UUID.randomUUID());
            approval.setWorkflow(entity);
            approval.setApproverUserId("user:treasurer");
            approval.setDecision("APPROVE");
            approval.setComments(null);
            approval.setApprovedAt(completedAt);
            entity.getApprovals().add(approval);

            ApprovalWorkflow workflow = mapper.toDomain(entity);

            assertThat(workflow.status()).isEqualTo(ApprovalWorkflow.Status.APPROVED);
            assertThat(workflow.completedAt()).isEqualTo(completedAt);
        }

        @Test
        @DisplayName("should map rejected entity to workflow")
        void shouldMapRejectedEntityToWorkflow() {
            Instant completedAt = Instant.now();
            ApprovalWorkflowEntity entity = createWorkflowEntity(
                "REFUND", "ref-101", 1, "REJECTED", "user:customer", completedAt
            );

            ApprovalEntity rejection = new ApprovalEntity();
            rejection.setId(UUID.randomUUID());
            rejection.setWorkflow(entity);
            rejection.setApproverUserId("user:support");
            rejection.setDecision("REJECT");
            rejection.setComments("Policy violation");
            rejection.setApprovedAt(completedAt);
            entity.getApprovals().add(rejection);

            ApprovalWorkflow workflow = mapper.toDomain(entity);

            assertThat(workflow.status()).isEqualTo(ApprovalWorkflow.Status.REJECTED);
        }

        @Test
        @DisplayName("should map expired entity to workflow")
        void shouldMapExpiredEntityToWorkflow() {
            Instant completedAt = Instant.now();
            ApprovalWorkflowEntity entity = createWorkflowEntity(
                "OFFER", "offer-202", 2, "EXPIRED", "user:sales", completedAt
            );

            ApprovalWorkflow workflow = mapper.toDomain(entity);

            assertThat(workflow.status()).isEqualTo(ApprovalWorkflow.Status.EXPIRED);
            assertThat(workflow.completedAt()).isEqualTo(completedAt);
        }
    }

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve pending workflow in round-trip")
        void shouldPreservePendingWorkflowInRoundTrip() {
            ApprovalWorkflow original = ApprovalWorkflow.initiate(
                "LEAVE_REQUEST",
                "leave-333",
                1,
                "user:employee"
            );

            ApprovalWorkflowEntity entity = mapper.toEntity(original);
            ApprovalWorkflow reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.id()).isEqualTo(original.id());
            assertThat(reconstructed.resourceType()).isEqualTo(original.resourceType());
            assertThat(reconstructed.resourceId()).isEqualTo(original.resourceId());
            assertThat(reconstructed.requiredApprovals()).isEqualTo(original.requiredApprovals());
            assertThat(reconstructed.status()).isEqualTo(original.status());
            assertThat(reconstructed.initiatedBy()).isEqualTo(original.initiatedBy());
        }

        @Test
        @DisplayName("should preserve approved workflow with approvals in round-trip")
        void shouldPreserveApprovedWorkflowWithApprovalsInRoundTrip() {
            ApprovalWorkflow original = ApprovalWorkflow.initiate(
                "TIMESHEET",
                "ts-444",
                1,
                "user:contractor"
            );
            original.recordApproval("user:supervisor", ApprovalWorkflow.Decision.APPROVE, "Confirmed hours");

            ApprovalWorkflowEntity entity = mapper.toEntity(original);
            ApprovalWorkflow reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.status()).isEqualTo(ApprovalWorkflow.Status.APPROVED);
            assertThat(reconstructed.approvals()).hasSize(1);
            assertThat(reconstructed.approvals().get(0).approverUserId()).isEqualTo("user:supervisor");
            assertThat(reconstructed.approvals().get(0).comments()).isEqualTo("Confirmed hours");
        }
    }

    private ApprovalWorkflowEntity createWorkflowEntity(String resourceType, String resourceId,
            int requiredApprovals, String status, String initiatedBy, Instant completedAt) {
        ApprovalWorkflowEntity entity = new ApprovalWorkflowEntity();
        entity.setId(UUID.randomUUID());
        entity.setResourceType(resourceType);
        entity.setResourceId(resourceId);
        entity.setRequiredApprovals(requiredApprovals);
        entity.setStatus(status);
        entity.setInitiatedBy(initiatedBy);
        entity.setInitiatedAt(Instant.now().minusSeconds(3600));
        entity.setCompletedAt(completedAt);
        entity.setApprovals(new ArrayList<>());
        return entity;
    }
}
