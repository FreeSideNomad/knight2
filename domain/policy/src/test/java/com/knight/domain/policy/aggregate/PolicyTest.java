package com.knight.domain.policy.aggregate;

import org.junit.jupiter.api.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Policy aggregate.
 */
@DisplayName("Policy Aggregate Tests")
class PolicyTest {

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("should create PERMISSION policy successfully")
        void shouldCreatePermissionPolicySuccessfully() {
            // When
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:ADMIN",
                "payments.*",
                "*",
                null
            );

            // Then
            assertThat(policy.id()).isNotNull();
            assertThat(policy.policyType()).isEqualTo(Policy.PolicyType.PERMISSION);
            assertThat(policy.subject()).isEqualTo("ROLE:ADMIN");
            assertThat(policy.action()).isEqualTo("payments.*");
            assertThat(policy.resource()).isEqualTo("*");
            assertThat(policy.approverCount()).isNull();
            assertThat(policy.createdAt()).isNotNull();
            assertThat(policy.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should create APPROVAL policy successfully")
        void shouldCreateApprovalPolicySuccessfully() {
            // When
            Policy policy = Policy.create(
                Policy.PolicyType.APPROVAL,
                "ROLE:MANAGER",
                "wire.transfer",
                "high-value:*",
                2
            );

            // Then
            assertThat(policy.id()).isNotNull();
            assertThat(policy.policyType()).isEqualTo(Policy.PolicyType.APPROVAL);
            assertThat(policy.subject()).isEqualTo("ROLE:MANAGER");
            assertThat(policy.action()).isEqualTo("wire.transfer");
            assertThat(policy.resource()).isEqualTo("high-value:*");
            assertThat(policy.approverCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("should throw when subject is null")
        void shouldThrowWhenSubjectIsNull() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.PERMISSION,
                null,
                "action",
                "resource",
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when subject is blank")
        void shouldThrowWhenSubjectIsBlank() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.PERMISSION,
                "  ",
                "action",
                "resource",
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subject cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when action is null")
        void shouldThrowWhenActionIsNull() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                null,
                "resource",
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Action cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when action is blank")
        void shouldThrowWhenActionIsBlank() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "",
                "resource",
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Action cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when resource is null")
        void shouldThrowWhenResourceIsNull() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "action",
                null,
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when resource is blank")
        void shouldThrowWhenResourceIsBlank() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "action",
                "   ",
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Resource cannot be null or blank");
        }

        @Test
        @DisplayName("should throw when APPROVAL policy has null approverCount")
        void shouldThrowWhenApprovalPolicyHasNullApproverCount() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.APPROVAL,
                "ROLE:MANAGER",
                "approve.payment",
                "*",
                null
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Approval policy requires approverCount > 0");
        }

        @Test
        @DisplayName("should throw when APPROVAL policy has zero approverCount")
        void shouldThrowWhenApprovalPolicyHasZeroApproverCount() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.APPROVAL,
                "ROLE:MANAGER",
                "approve.payment",
                "*",
                0
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Approval policy requires approverCount > 0");
        }

        @Test
        @DisplayName("should throw when APPROVAL policy has negative approverCount")
        void shouldThrowWhenApprovalPolicyHasNegativeApproverCount() {
            assertThatThrownBy(() -> Policy.create(
                Policy.PolicyType.APPROVAL,
                "ROLE:MANAGER",
                "approve.payment",
                "*",
                -1
            )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Approval policy requires approverCount > 0");
        }

        @Test
        @DisplayName("should throw when policyType is null")
        void shouldThrowWhenPolicyTypeIsNull() {
            assertThatThrownBy(() -> Policy.create(
                null,
                "ROLE:USER",
                "action",
                "resource",
                null
            )).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("should update action")
        void shouldUpdateAction() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "old.action",
                "resource",
                null
            );

            // When
            policy.update("new.action", null, null);

            // Then
            assertThat(policy.action()).isEqualTo("new.action");
        }

        @Test
        @DisplayName("should update resource")
        void shouldUpdateResource() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "action",
                "old.resource",
                null
            );

            // When
            policy.update(null, "new.resource", null);

            // Then
            assertThat(policy.resource()).isEqualTo("new.resource");
        }

        @Test
        @DisplayName("should update both action and resource")
        void shouldUpdateBothActionAndResource() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "old.action",
                "old.resource",
                null
            );

            // When
            policy.update("new.action", "new.resource", null);

            // Then
            assertThat(policy.action()).isEqualTo("new.action");
            assertThat(policy.resource()).isEqualTo("new.resource");
        }

        @Test
        @DisplayName("should update approverCount for APPROVAL policy")
        void shouldUpdateApproverCountForApprovalPolicy() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.APPROVAL,
                "ROLE:MANAGER",
                "approve",
                "payment",
                2
            );

            // When
            policy.update(null, null, 3);

            // Then
            assertThat(policy.approverCount()).isEqualTo(3);
        }

        @Test
        @DisplayName("should throw when updating APPROVAL policy with invalid approverCount")
        void shouldThrowWhenUpdatingApprovalPolicyWithInvalidApproverCount() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.APPROVAL,
                "ROLE:MANAGER",
                "approve",
                "payment",
                2
            );

            // When/Then
            assertThatThrownBy(() -> policy.update(null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Approval policy requires approverCount > 0");
        }

        @Test
        @DisplayName("should not update action when blank")
        void shouldNotUpdateActionWhenBlank() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "original.action",
                "resource",
                null
            );

            // When
            policy.update("  ", null, null);

            // Then
            assertThat(policy.action()).isEqualTo("original.action");
        }

        @Test
        @DisplayName("should not update resource when blank")
        void shouldNotUpdateResourceWhenBlank() {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "action",
                "original.resource",
                null
            );

            // When
            policy.update(null, "", null);

            // Then
            assertThat(policy.resource()).isEqualTo("original.resource");
        }

        @Test
        @DisplayName("should update updatedAt timestamp")
        void shouldUpdateUpdatedAtTimestamp() throws Exception {
            // Given
            Policy policy = Policy.create(
                Policy.PolicyType.PERMISSION,
                "ROLE:USER",
                "action",
                "resource",
                null
            );
            var originalUpdatedAt = policy.updatedAt();

            // Wait a bit to ensure timestamp difference
            Thread.sleep(10);

            // When
            policy.update("new.action", null, null);

            // Then
            assertThat(policy.updatedAt()).isAfter(originalUpdatedAt);
        }
    }

    @Nested
    @DisplayName("PolicyType enum")
    class PolicyTypeEnum {

        @Test
        @DisplayName("should have PERMISSION type")
        void shouldHavePermissionType() {
            assertThat(Policy.PolicyType.valueOf("PERMISSION")).isEqualTo(Policy.PolicyType.PERMISSION);
        }

        @Test
        @DisplayName("should have APPROVAL type")
        void shouldHaveApprovalType() {
            assertThat(Policy.PolicyType.valueOf("APPROVAL")).isEqualTo(Policy.PolicyType.APPROVAL);
        }
    }
}
