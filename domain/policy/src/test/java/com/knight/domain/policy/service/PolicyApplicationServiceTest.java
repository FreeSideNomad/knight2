package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.Policy;
import com.knight.domain.policy.api.commands.PolicyCommands.CreatePolicyCmd;
import com.knight.domain.policy.api.commands.PolicyCommands.DeletePolicyCmd;
import com.knight.domain.policy.api.commands.PolicyCommands.UpdatePolicyCmd;
import com.knight.domain.policy.api.events.PolicyCreated;
import com.knight.domain.policy.api.queries.PolicyQueries.PolicySummary;
import com.knight.domain.policy.repository.PolicyRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PolicyApplicationService.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PolicyApplicationService Tests")
class PolicyApplicationServiceTest {

    @Mock
    private PolicyRepository repository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private PolicyApplicationService service;

    @Nested
    @DisplayName("createPolicy()")
    class CreatePolicy {

        @Test
        @DisplayName("should create PERMISSION policy successfully")
        void shouldCreatePermissionPolicySuccessfully() {
            // Given
            CreatePolicyCmd cmd = new CreatePolicyCmd(
                "PERMISSION",
                "ROLE:ADMIN",
                "payments.*",
                "*",
                null
            );

            // When
            String policyId = service.createPolicy(cmd);

            // Then
            assertThat(policyId).isNotNull();

            // Verify policy was saved
            ArgumentCaptor<Policy> policyCaptor = ArgumentCaptor.forClass(Policy.class);
            verify(repository).save(policyCaptor.capture());

            Policy savedPolicy = policyCaptor.getValue();
            assertThat(savedPolicy.policyType()).isEqualTo(Policy.PolicyType.PERMISSION);
            assertThat(savedPolicy.subject()).isEqualTo("ROLE:ADMIN");
            assertThat(savedPolicy.action()).isEqualTo("payments.*");
            assertThat(savedPolicy.resource()).isEqualTo("*");
        }

        @Test
        @DisplayName("should create APPROVAL policy successfully")
        void shouldCreateApprovalPolicySuccessfully() {
            // Given
            CreatePolicyCmd cmd = new CreatePolicyCmd(
                "APPROVAL",
                "ROLE:MANAGER",
                "wire.transfer",
                "high-value:*",
                2
            );

            // When
            String policyId = service.createPolicy(cmd);

            // Then
            assertThat(policyId).isNotNull();

            ArgumentCaptor<Policy> policyCaptor = ArgumentCaptor.forClass(Policy.class);
            verify(repository).save(policyCaptor.capture());

            Policy savedPolicy = policyCaptor.getValue();
            assertThat(savedPolicy.policyType()).isEqualTo(Policy.PolicyType.APPROVAL);
            assertThat(savedPolicy.approverCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should publish PolicyCreated event")
        void shouldPublishPolicyCreatedEvent() {
            // Given
            CreatePolicyCmd cmd = new CreatePolicyCmd(
                "PERMISSION",
                "ROLE:USER",
                "accounts.view",
                "*",
                null
            );

            // When
            service.createPolicy(cmd);

            // Then
            ArgumentCaptor<PolicyCreated> eventCaptor = ArgumentCaptor.forClass(PolicyCreated.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            PolicyCreated event = eventCaptor.getValue();
            assertThat(event.policyId()).isNotNull();
            assertThat(event.policyType()).isEqualTo("PERMISSION");
            assertThat(event.subject()).isEqualTo("ROLE:USER");
            assertThat(event.createdAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("updatePolicy()")
    class UpdatePolicy {

        @Test
        @DisplayName("should update policy successfully")
        void shouldUpdatePolicySuccessfully() {
            // Given
            Policy existingPolicy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "old.action",
                "old.resource",
                null
            );
            when(repository.findById(existingPolicy.id())).thenReturn(Optional.of(existingPolicy));

            UpdatePolicyCmd cmd = new UpdatePolicyCmd(
                existingPolicy.id(),
                "new.action",
                "new.resource",
                null
            );

            // When
            service.updatePolicy(cmd);

            // Then
            verify(repository).save(any(Policy.class));
            assertThat(existingPolicy.action()).isEqualTo("new.action");
            assertThat(existingPolicy.resource()).isEqualTo("new.resource");
        }

        @Test
        @DisplayName("should throw when policy not found")
        void shouldThrowWhenPolicyNotFound() {
            // Given
            when(repository.findById("non-existent")).thenReturn(Optional.empty());

            UpdatePolicyCmd cmd = new UpdatePolicyCmd(
                "non-existent",
                "action",
                "resource",
                null
            );

            // When/Then
            assertThatThrownBy(() -> service.updatePolicy(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Policy not found");
        }

        @Test
        @DisplayName("should update approverCount for APPROVAL policy")
        void shouldUpdateApproverCountForApprovalPolicy() {
            // Given
            Policy existingPolicy = Policy.create(
                Policy.PolicyType.APPROVAL,
                "ROLE:MANAGER",
                "approve",
                "payment",
                2
            );
            when(repository.findById(existingPolicy.id())).thenReturn(Optional.of(existingPolicy));

            UpdatePolicyCmd cmd = new UpdatePolicyCmd(
                existingPolicy.id(),
                null,
                null,
                3
            );

            // When
            service.updatePolicy(cmd);

            // Then
            assertThat(existingPolicy.approverCount()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("deletePolicy()")
    class DeletePolicy {

        @Test
        @DisplayName("should delete policy successfully")
        void shouldDeletePolicySuccessfully() {
            // Given
            DeletePolicyCmd cmd = new DeletePolicyCmd("policy-id");

            // When
            service.deletePolicy(cmd);

            // Then
            verify(repository).deleteById("policy-id");
        }
    }

    @Nested
    @DisplayName("getPolicySummary()")
    class GetPolicySummary {

        @Test
        @DisplayName("should return policy summary")
        void shouldReturnPolicySummary() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:ADMIN",
                "payments.*",
                "*",
                null
            );
            when(repository.findById(policy.id())).thenReturn(Optional.of(policy));

            // When
            PolicySummary summary = service.getPolicySummary(policy.id());

            // Then
            assertThat(summary.policyId()).isEqualTo(policy.id());
            assertThat(summary.policyType()).isEqualTo("PERMISSION");
            assertThat(summary.subject()).isEqualTo("ROLE:ADMIN");
            assertThat(summary.action()).isEqualTo("payments.*");
            assertThat(summary.resource()).isEqualTo("*");
            assertThat(summary.approverCount()).isNull();
        }

        @Test
        @DisplayName("should return approval policy summary with approverCount")
        void shouldReturnApprovalPolicySummaryWithApproverCount() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.APPROVAL,
                "ROLE:MANAGER",
                "approve.transfer",
                "high-value",
                2
            );
            when(repository.findById(policy.id())).thenReturn(Optional.of(policy));

            // When
            PolicySummary summary = service.getPolicySummary(policy.id());

            // Then
            assertThat(summary.policyType()).isEqualTo("APPROVAL");
            assertThat(summary.approverCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw when policy not found")
        void shouldThrowWhenPolicyNotFound() {
            // Given
            when(repository.findById("non-existent")).thenReturn(Optional.empty());

            // When/Then
            assertThatThrownBy(() -> service.getPolicySummary("non-existent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Policy not found");
        }
    }
}
