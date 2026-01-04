package com.knight.domain.approvalworkflows.service;

import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow;
import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow.Decision;
import com.knight.domain.approvalworkflows.aggregate.ApprovalWorkflow.Status;
import com.knight.domain.approvalworkflows.api.commands.ApprovalWorkflowCommands.*;
import com.knight.domain.approvalworkflows.api.events.WorkflowInitiated;
import com.knight.domain.approvalworkflows.api.queries.ApprovalWorkflowQueries.ApprovalWorkflowSummary;
import com.knight.domain.approvalworkflows.repository.ApprovalWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApprovalWorkflowApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class ApprovalWorkflowApplicationServiceTest {

    @Mock
    private ApprovalWorkflowRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Captor
    private ArgumentCaptor<ApprovalWorkflow> workflowCaptor;

    @Captor
    private ArgumentCaptor<WorkflowInitiated> eventCaptor;

    private ApprovalWorkflowApplicationService service;

    private static final String RESOURCE_TYPE = "payment";
    private static final String RESOURCE_ID = "payment-123";
    private static final String INITIATED_BY = "admin@example.com";
    private static final String APPROVER = "approver@example.com";

    @BeforeEach
    void setUp() {
        service = new ApprovalWorkflowApplicationService(repository, eventPublisher);
    }

    // ==================== Initiate Workflow Tests ====================

    @Nested
    @DisplayName("initiateWorkflow()")
    class InitiateWorkflowTests {

        @Test
        @DisplayName("should create workflow and return ID")
        void shouldCreateWorkflowAndReturnId() {
            InitiateWorkflowCmd cmd = new InitiateWorkflowCmd(
                RESOURCE_TYPE, RESOURCE_ID, 2, INITIATED_BY
            );

            String workflowId = service.initiateWorkflow(cmd);

            assertThat(workflowId).isNotNull();
            verify(repository).save(workflowCaptor.capture());
            ApprovalWorkflow savedWorkflow = workflowCaptor.getValue();
            assertThat(savedWorkflow.resourceType()).isEqualTo(RESOURCE_TYPE);
            assertThat(savedWorkflow.resourceId()).isEqualTo(RESOURCE_ID);
            assertThat(savedWorkflow.requiredApprovals()).isEqualTo(2);
            assertThat(savedWorkflow.initiatedBy()).isEqualTo(INITIATED_BY);
            assertThat(savedWorkflow.status()).isEqualTo(Status.PENDING);
        }

        @Test
        @DisplayName("should publish WorkflowInitiated event")
        void shouldPublishWorkflowInitiatedEvent() {
            InitiateWorkflowCmd cmd = new InitiateWorkflowCmd(
                RESOURCE_TYPE, RESOURCE_ID, 1, INITIATED_BY
            );

            String workflowId = service.initiateWorkflow(cmd);

            verify(eventPublisher).publishEvent(eventCaptor.capture());
            WorkflowInitiated event = eventCaptor.getValue();
            assertThat(event.workflowId()).isEqualTo(workflowId);
            assertThat(event.resourceType()).isEqualTo(RESOURCE_TYPE);
            assertThat(event.resourceId()).isEqualTo(RESOURCE_ID);
            assertThat(event.requiredApprovals()).isEqualTo(1);
            assertThat(event.initiatedAt()).isNotNull();
        }
    }

    // ==================== Record Approval Tests ====================

    @Nested
    @DisplayName("recordApproval()")
    class RecordApprovalTests {

        @Test
        @DisplayName("should record approval on existing workflow")
        void shouldRecordApprovalOnExistingWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 2, INITIATED_BY);
            when(repository.findById(workflow.id())).thenReturn(Optional.of(workflow));

            RecordApprovalCmd cmd = new RecordApprovalCmd(
                workflow.id(), APPROVER, "APPROVE", "Looks good"
            );
            service.recordApproval(cmd);

            verify(repository).save(workflowCaptor.capture());
            ApprovalWorkflow savedWorkflow = workflowCaptor.getValue();
            assertThat(savedWorkflow.approvals()).hasSize(1);
            assertThat(savedWorkflow.approvals().get(0).approverUserId()).isEqualTo(APPROVER);
            assertThat(savedWorkflow.approvals().get(0).decision()).isEqualTo(Decision.APPROVE);
        }

        @Test
        @DisplayName("should record rejection on existing workflow")
        void shouldRecordRejectionOnExistingWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 2, INITIATED_BY);
            when(repository.findById(workflow.id())).thenReturn(Optional.of(workflow));

            RecordApprovalCmd cmd = new RecordApprovalCmd(
                workflow.id(), APPROVER, "REJECT", "Not approved"
            );
            service.recordApproval(cmd);

            verify(repository).save(workflowCaptor.capture());
            ApprovalWorkflow savedWorkflow = workflowCaptor.getValue();
            assertThat(savedWorkflow.status()).isEqualTo(Status.REJECTED);
        }

        @Test
        @DisplayName("should throw exception when workflow not found")
        void shouldThrowExceptionWhenWorkflowNotFound() {
            when(repository.findById("non-existent")).thenReturn(Optional.empty());

            RecordApprovalCmd cmd = new RecordApprovalCmd(
                "non-existent", APPROVER, "APPROVE", null
            );

            assertThatThrownBy(() -> service.recordApproval(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow not found: non-existent");
        }
    }

    // ==================== Expire Workflow Tests ====================

    @Nested
    @DisplayName("expireWorkflow()")
    class ExpireWorkflowTests {

        @Test
        @DisplayName("should expire pending workflow")
        void shouldExpirePendingWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 1, INITIATED_BY);
            when(repository.findById(workflow.id())).thenReturn(Optional.of(workflow));

            ExpireWorkflowCmd cmd = new ExpireWorkflowCmd(workflow.id());
            service.expireWorkflow(cmd);

            verify(repository).save(workflowCaptor.capture());
            ApprovalWorkflow savedWorkflow = workflowCaptor.getValue();
            assertThat(savedWorkflow.status()).isEqualTo(Status.EXPIRED);
        }

        @Test
        @DisplayName("should throw exception when workflow not found")
        void shouldThrowExceptionWhenWorkflowNotFound() {
            when(repository.findById("non-existent")).thenReturn(Optional.empty());

            ExpireWorkflowCmd cmd = new ExpireWorkflowCmd("non-existent");

            assertThatThrownBy(() -> service.expireWorkflow(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow not found: non-existent");
        }
    }

    // ==================== Get Workflow Summary Tests ====================

    @Nested
    @DisplayName("getWorkflowSummary()")
    class GetWorkflowSummaryTests {

        @Test
        @DisplayName("should return summary for pending workflow")
        void shouldReturnSummaryForPendingWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 2, INITIATED_BY);
            when(repository.findById(workflow.id())).thenReturn(Optional.of(workflow));

            ApprovalWorkflowSummary summary = service.getWorkflowSummary(workflow.id());

            assertThat(summary.workflowId()).isEqualTo(workflow.id());
            assertThat(summary.status()).isEqualTo("PENDING");
            assertThat(summary.resourceType()).isEqualTo(RESOURCE_TYPE);
            assertThat(summary.resourceId()).isEqualTo(RESOURCE_ID);
            assertThat(summary.requiredApprovals()).isEqualTo(2);
            assertThat(summary.receivedApprovals()).isEqualTo(0);
        }

        @Test
        @DisplayName("should return summary with approval count")
        void shouldReturnSummaryWithApprovalCount() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 2, INITIATED_BY);
            workflow.recordApproval(APPROVER, Decision.APPROVE, null);
            when(repository.findById(workflow.id())).thenReturn(Optional.of(workflow));

            ApprovalWorkflowSummary summary = service.getWorkflowSummary(workflow.id());

            assertThat(summary.receivedApprovals()).isEqualTo(1);
            assertThat(summary.requiredApprovals()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw exception when workflow not found")
        void shouldThrowExceptionWhenWorkflowNotFound() {
            when(repository.findById("non-existent")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getWorkflowSummary("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Workflow not found: non-existent");
        }

        @Test
        @DisplayName("should return summary for approved workflow")
        void shouldReturnSummaryForApprovedWorkflow() {
            ApprovalWorkflow workflow = ApprovalWorkflow.initiate(RESOURCE_TYPE, RESOURCE_ID, 1, INITIATED_BY);
            workflow.recordApproval(APPROVER, Decision.APPROVE, null);
            when(repository.findById(workflow.id())).thenReturn(Optional.of(workflow));

            ApprovalWorkflowSummary summary = service.getWorkflowSummary(workflow.id());

            assertThat(summary.status()).isEqualTo("APPROVED");
            assertThat(summary.receivedApprovals()).isEqualTo(1);
        }
    }
}
