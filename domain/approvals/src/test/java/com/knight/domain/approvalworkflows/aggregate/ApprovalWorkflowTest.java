package com.knight.domain.approvalworkflows.aggregate;

import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow.Approval;
import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow.Decision;
import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ApprovalWorkflow aggregate.
 */
class ApprovalWorkflowTest {

    private static final String RESOURCE_TYPE = "payment";
    private static final String RESOURCE_ID = "payment-123";
    private static final String INITIATED_BY = "admin@example.com";
    private static final String APPROVER_1 = "approver1@example.com";
    private static final String APPROVER_2 = "approver2@example.com";

    // ==================== Initiate Tests ====================

    @Nested
    @DisplayName("initiate()")
    class InitiateTests {

        @Test
        @DisplayName("should create workflow with valid parameters")
        void shouldCreateWorkflowWithValidParameters() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(
                RESOURCE_TYPE, RESOURCE_ID, 2, INITIATED_BY
            );

            assertThat(workflow.id()).isNotNull();
            assertThat(workflow.resourceType()).isEqualTo(RESOURCE_TYPE);
            assertThat(workflow.resourceId()).isEqualTo(RESOURCE_ID);
            assertThat(workflow.requiredApprovals()).isEqualTo(2);
            assertThat(workflow.initiatedBy()).isEqualTo(INITIATED_BY);
            assertThat(workflow.status()).isEqualTo(Status.PENDING);
            assertThat(workflow.approvals()).isEmpty();
            assertThat(workflow.initiatedAt()).isNotNull();
            assertThat(workflow.completedAt()).isNull();
        }

        @Test
        @DisplayName("should generate unique IDs for each workflow")
        void shouldGenerateUniqueIdsForEachWorkflow() {
            ApprovalWorkflow workflow1 = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 1, INITIATED_BY);
            ApprovalWorkflow workflow2 = ApprovalWorkflow.initiate(RESOURCE_TYPE, "other-id", 1, INITIATED_BY);

            assertThat(workflow1.id()).isNotEqualTo(workflow2.id());
        }

        @Test
        @DisplayName("should throw exception for null resource type")
        void shouldThrowExceptionForNullResourceType() {
            assertThatThrownBy(() -> ApprovalWorkflow.initiate(null, RESOURCE_ID, 1, INITIATED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resource type cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception for blank resource type")
        void shouldThrowExceptionForBlankResourceType() {
            assertThatThrownBy(() -> ApprovalWorkflow.initiate("   ", RESOURCE_ID, 1, INITIATED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resource type cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception for null resource ID")
        void shouldThrowExceptionForNullResourceId() {
            assertThatThrownBy(() -> ApprovalWorkflow.initiate(RESOURCE_TYPE, null, 1, INITIATED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resource ID cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception for blank resource ID")
        void shouldThrowExceptionForBlankResourceId() {
            assertThatThrownBy(() -> ApprovalWorkflow.initiate(RESOURCE_TYPE, "", 1, INITIATED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Resource ID cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception for zero required approvals")
        void shouldThrowExceptionForZeroRequiredApprovals() {
            assertThatThrownBy(() -> ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 0, INITIATED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Required approvals must be greater than 0");
        }

        @Test
        @DisplayName("should throw exception for negative required approvals")
        void shouldThrowExceptionForNegativeRequiredApprovals() {
            assertThatThrownBy(() -> ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, -1, INITIATED_BY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Required approvals must be greater than 0");
        }

        @Test
        @DisplayName("should throw exception for null initiated by")
        void shouldThrowExceptionForNullInitiatedBy() {
            assertThatThrownBy(() -> ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 1, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Initiated by cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception for blank initiated by")
        void shouldThrowExceptionForBlankInitiatedBy() {
            assertThatThrownBy(() -> ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 1, "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Initiated by cannot be null or blank");
        }
    }

    // ==================== Record Approval Tests ====================

    @Nested
    @DisplayName("recordApproval()")
    class RecordApprovalTests {

        private ApprovalWorkflow workflow;

        @BeforeEach
        void setUp() {
            workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 2, INITIATED_BY);
        }

        @Test
        @DisplayName("should record approve decision")
        void shouldRecordApproveDecision() {
            workflow.recordApproval(APPROVER_1, Decision.APPROVE, "Looks good");

            assertThat(workflow.approvals()).hasSize(1);
            Approval approval = workflow.approvals().get(0);
            assertThat(approval.approverUserId()).isEqualTo(APPROVER_1);
            assertThat(approval.decision()).isEqualTo(Decision.APPROVE);
            assertThat(approval.comments()).isEqualTo("Looks good");
            assertThat(approval.approvedAt()).isNotNull();
            assertThat(approval.approvalId()).isNotNull();
            assertThat(workflow.status()).isEqualTo(Status.PENDING);
        }

        @Test
        @DisplayName("should remain PENDING when approvals < required")
        void shouldRemainPendingWhenApprovalsLessThanRequired() {
            workflow.recordApproval(APPROVER_1, Decision.APPROVE, null);

            assertThat(workflow.status()).isEqualTo(Status.PENDING);
            assertThat(workflow.completedAt()).isNull();
        }

        @Test
        @DisplayName("should transition to APPROVED when approvals >= required")
        void shouldTransitionToApprovedWhenEnoughApprovals() {
            workflow.recordApproval(APPROVER_1, Decision.APPROVE, null);
            workflow.recordApproval(APPROVER_2, Decision.APPROVE, null);

            assertThat(workflow.status()).isEqualTo(Status.APPROVED);
            assertThat(workflow.completedAt()).isNotNull();
        }

        @Test
        @DisplayName("should immediately reject on REJECT decision")
        void shouldImmediatelyRejectOnRejectDecision() {
            workflow.recordApproval(APPROVER_1, Decision.REJECT, "Not approved");

            assertThat(workflow.status()).isEqualTo(Status.REJECTED);
            assertThat(workflow.completedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw exception when recording on non-pending workflow")
        void shouldThrowExceptionWhenRecordingOnNonPendingWorkflow() {
            workflow.recordApproval(APPROVER_1, Decision.REJECT, null);

            assertThatThrownBy(() -> workflow.recordApproval(APPROVER_2, Decision.APPROVE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot record approval for workflow in status: REJECTED");
        }

        @Test
        @DisplayName("should throw exception for null approver user ID")
        void shouldThrowExceptionForNullApproverUserId() {
            assertThatThrownBy(() -> workflow.recordApproval(null, Decision.APPROVE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Approver user ID cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception for blank approver user ID")
        void shouldThrowExceptionForBlankApproverUserId() {
            assertThatThrownBy(() -> workflow.recordApproval("  ", Decision.APPROVE, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Approver user ID cannot be null or blank");
        }

        @Test
        @DisplayName("should throw exception when same user tries to approve twice")
        void shouldThrowExceptionWhenSameUserTriesToApproveTwice() {
            workflow.recordApproval(APPROVER_1, Decision.APPROVE, null);

            assertThatThrownBy(() -> workflow.recordApproval(APPROVER_1, Decision.APPROVE, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("User has already provided approval: " + APPROVER_1);
        }

        @Test
        @DisplayName("should handle null comments")
        void shouldHandleNullComments() {
            workflow.recordApproval(APPROVER_1, Decision.APPROVE, null);

            Approval approval = workflow.approvals().get(0);
            assertThat(approval.comments()).isNull();
        }
    }

    // ==================== Expire Tests ====================

    @Nested
    @DisplayName("expire()")
    class ExpireTests {

        @Test
        @DisplayName("should expire pending workflow")
        void shouldExpirePendingWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 1, INITIATED_BY);

            workflow.expire();

            assertThat(workflow.status()).isEqualTo(Status.EXPIRED);
            assertThat(workflow.completedAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw exception when expiring non-pending workflow")
        void shouldThrowExceptionWhenExpiringNonPendingWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 1, INITIATED_BY);
            workflow.recordApproval(APPROVER_1, Decision.APPROVE, null);

            assertThatThrownBy(workflow::expire)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot expire workflow in status: APPROVED");
        }
    }

    // ==================== Reconstitute Tests ====================

    @Nested
    @DisplayName("reconstitute()")
    class ReconstituteTests {

        @Test
        @DisplayName("should reconstitute workflow with all fields")
        void shouldReconstituteWorkflowWithAllFields() {
            String id = "workflow-123";
            Instant initiatedAt = Instant.parse("2024-01-15T10:00:00Z");
            Instant completedAt = Instant.parse("2024-01-15T12:00:00Z");
            List<Approval> approvals = List.of(
                Approval.reconstitute("approval-1", APPROVER_1, Decision.APPROVE, "OK", initiatedAt)
            );

            ApprovalWorkflow workflow = ApprovalWorkflow.reconstitute(
                id, RESOURCE_TYPE, RESOURCE_ID, 1, Status.APPROVED,
                approvals, initiatedAt, INITIATED_BY, completedAt
            );

            assertThat(workflow.id()).isEqualTo(id);
            assertThat(workflow.resourceType()).isEqualTo(RESOURCE_TYPE);
            assertThat(workflow.resourceId()).isEqualTo(RESOURCE_ID);
            assertThat(workflow.requiredApprovals()).isEqualTo(1);
            assertThat(workflow.status()).isEqualTo(Status.APPROVED);
            assertThat(workflow.approvals()).hasSize(1);
            assertThat(workflow.initiatedAt()).isEqualTo(initiatedAt);
            assertThat(workflow.initiatedBy()).isEqualTo(INITIATED_BY);
            assertThat(workflow.completedAt()).isEqualTo(completedAt);
        }

        @Test
        @DisplayName("should reconstitute workflow with empty approvals")
        void shouldReconstituteWorkflowWithEmptyApprovals() {
            String id = "workflow-456";
            Instant initiatedAt = Instant.now();

            ApprovalWorkflow workflow = ApprovalWorkflow.reconstitute(
                id, RESOURCE_TYPE, RESOURCE_ID, 2, Status.PENDING,
                List.of(), initiatedAt, INITIATED_BY, null
            );

            assertThat(workflow.approvals()).isEmpty();
            assertThat(workflow.status()).isEqualTo(Status.PENDING);
            assertThat(workflow.completedAt()).isNull();
        }
    }

    // ==================== Approval Value Object Tests ====================

    @Nested
    @DisplayName("Approval Value Object")
    class ApprovalTests {

        @Test
        @DisplayName("should create approval with all fields")
        void shouldCreateApprovalWithAllFields() {
            Approval approval = new Approval(APPROVER_1, Decision.APPROVE, "Approved");

            assertThat(approval.approvalId()).isNotNull();
            assertThat(approval.approverUserId()).isEqualTo(APPROVER_1);
            assertThat(approval.decision()).isEqualTo(Decision.APPROVE);
            assertThat(approval.comments()).isEqualTo("Approved");
            assertThat(approval.approvedAt()).isNotNull();
        }

        @Test
        @DisplayName("should reconstitute approval with specific values")
        void shouldReconstituteApprovalWithSpecificValues() {
            String approvalId = "approval-789";
            Instant approvedAt = Instant.parse("2024-01-15T11:00:00Z");

            Approval approval = Approval.reconstitute(
                approvalId, APPROVER_1, Decision.REJECT, "Rejected", approvedAt
            );

            assertThat(approval.approvalId()).isEqualTo(approvalId);
            assertThat(approval.approverUserId()).isEqualTo(APPROVER_1);
            assertThat(approval.decision()).isEqualTo(Decision.REJECT);
            assertThat(approval.comments()).isEqualTo("Rejected");
            assertThat(approval.approvedAt()).isEqualTo(approvedAt);
        }
    }

    // ==================== Status and Decision Enum Tests ====================

    @Nested
    @DisplayName("Enums")
    class EnumTests {

        @Test
        @DisplayName("Status enum should have all expected values")
        void statusEnumShouldHaveAllExpectedValues() {
            assertThat(Status.values()).containsExactly(
                Status.PENDING, Status.APPROVED, Status.REJECTED, Status.EXPIRED
            );
        }

        @Test
        @DisplayName("Decision enum should have all expected values")
        void decisionEnumShouldHaveAllExpectedValues() {
            assertThat(Decision.values()).containsExactly(
                Decision.APPROVE, Decision.REJECT
            );
        }
    }

    // ==================== Immutability Tests ====================

    @Nested
    @DisplayName("Immutability")
    class ImmutabilityTests {

        @Test
        @DisplayName("approvals() should return immutable list")
        void approvalsShouldReturnImmutableList() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 1, INITIATED_BY);
            workflow.recordApproval(APPROVER_1, Decision.APPROVE, null);

            List<Approval> approvals = workflow.approvals();

            assertThatThrownBy(approvals::clear)
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}
