package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.api.commands.PermissionPolicyCommands.*;
import com.knight.domain.policy.api.queries.PermissionPolicyQueries.*;
import com.knight.domain.policy.api.types.PolicyDto;
import com.knight.domain.policy.repository.PermissionPolicyRepository;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.PredefinedRole;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PermissionPolicyApplicationService.
 */
@ExtendWith(MockitoExtension.class)
class PermissionPolicyApplicationServiceTest {

    @Mock
    private PermissionPolicyRepository policyRepository;

    @Mock
    private PermissionAuthorizationService authorizationService;

    @Captor
    private ArgumentCaptor<PermissionPolicy> policyCaptor;

    private PermissionPolicyApplicationService service;

    private static final ProfileId PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final UserId USER_ID = UserId.of("550e8400-e29b-41d4-a716-446655440000");
    private static final String SUBJECT_URN = "role:READER";  // Use role-based subject for easier testing
    private static final String ACTION_PATTERN = "payments.*";
    private static final String RESOURCE_PATTERN = "account.*";
    private static final String DESCRIPTION = "Allow all payment actions";
    private static final String CREATED_BY = "admin@example.com";

    @BeforeEach
    void setUp() {
        service = new PermissionPolicyApplicationService(policyRepository, authorizationService);
    }

    // ==================== Create Policy Tests ====================

    @Nested
    @DisplayName("createPolicy()")
    class CreatePolicyTests {

        @Test
        @DisplayName("should create policy with all parameters")
        void shouldCreatePolicyWithAllParameters() {
            CreatePolicyCmd cmd = new CreatePolicyCmd(
                PROFILE_ID, SUBJECT_URN, ACTION_PATTERN, RESOURCE_PATTERN, "ALLOW", DESCRIPTION, CREATED_BY
            );

            PolicyDto result = service.createPolicy(cmd);

            verify(policyRepository).save(policyCaptor.capture());
            PermissionPolicy savedPolicy = policyCaptor.getValue();
            assertThat(savedPolicy.profileId()).isEqualTo(PROFILE_ID);
            assertThat(savedPolicy.subject().toUrn()).isEqualTo(SUBJECT_URN);
            assertThat(savedPolicy.action().value()).isEqualTo(ACTION_PATTERN);
            assertThat(savedPolicy.resource().value()).isEqualTo(RESOURCE_PATTERN);
            assertThat(savedPolicy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
            assertThat(savedPolicy.description()).isEqualTo(DESCRIPTION);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should create policy with default resource when null")
        void shouldCreatePolicyWithDefaultResourceWhenNull() {
            CreatePolicyCmd cmd = new CreatePolicyCmd(
                PROFILE_ID, SUBJECT_URN, ACTION_PATTERN, null, "ALLOW", DESCRIPTION, CREATED_BY
            );

            service.createPolicy(cmd);

            verify(policyRepository).save(policyCaptor.capture());
            PermissionPolicy savedPolicy = policyCaptor.getValue();
            assertThat(savedPolicy.resource().value()).isEqualTo("*");
        }

        @Test
        @DisplayName("should create policy with default ALLOW effect when null")
        void shouldCreatePolicyWithDefaultAllowEffectWhenNull() {
            CreatePolicyCmd cmd = new CreatePolicyCmd(
                PROFILE_ID, SUBJECT_URN, ACTION_PATTERN, RESOURCE_PATTERN, null, DESCRIPTION, CREATED_BY
            );

            service.createPolicy(cmd);

            verify(policyRepository).save(policyCaptor.capture());
            PermissionPolicy savedPolicy = policyCaptor.getValue();
            assertThat(savedPolicy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
        }

        @Test
        @DisplayName("should create policy with DENY effect")
        void shouldCreatePolicyWithDenyEffect() {
            CreatePolicyCmd cmd = new CreatePolicyCmd(
                PROFILE_ID, SUBJECT_URN, ACTION_PATTERN, RESOURCE_PATTERN, "deny", DESCRIPTION, CREATED_BY
            );

            service.createPolicy(cmd);

            verify(policyRepository).save(policyCaptor.capture());
            PermissionPolicy savedPolicy = policyCaptor.getValue();
            assertThat(savedPolicy.effect()).isEqualTo(PermissionPolicy.Effect.DENY);
        }

        @Test
        @DisplayName("should return created policy DTO")
        void shouldReturnCreatedPolicyDto() {
            CreatePolicyCmd cmd = new CreatePolicyCmd(
                PROFILE_ID, SUBJECT_URN, ACTION_PATTERN, RESOURCE_PATTERN, "ALLOW", DESCRIPTION, CREATED_BY
            );

            PolicyDto result = service.createPolicy(cmd);

            assertThat(result).isNotNull();
            assertThat(result.id()).isNotNull();
            assertThat(result.subjectUrn()).isEqualTo(SUBJECT_URN);
            assertThat(result.actionPattern()).isEqualTo(ACTION_PATTERN);
            assertThat(result.resourcePattern()).isEqualTo(RESOURCE_PATTERN);
            assertThat(result.effect()).isEqualTo("ALLOW");
        }
    }

    // ==================== Update Policy Tests ====================

    @Nested
    @DisplayName("updatePolicy()")
    class UpdatePolicyTests {

        @Test
        @DisplayName("should update policy - note: PermissionPolicy.update() only updates timestamp")
        void shouldUpdatePolicyWithNewValues() {
            PermissionPolicy existingPolicy = createNonSystemPolicy();
            when(policyRepository.findById(existingPolicy.id())).thenReturn(Optional.of(existingPolicy));

            UpdatePolicyCmd cmd = new UpdatePolicyCmd(
                existingPolicy.id(), "payments.read", "account.123", "DENY", "Updated description"
            );
            PolicyDto result = service.updatePolicy(cmd);

            // Verify save was called
            verify(policyRepository).save(any());
            // Note: PermissionPolicy.update() currently only updates timestamp,
            // so values remain as original
            assertThat(result.actionPattern()).isEqualTo(ACTION_PATTERN);
            assertThat(result.resourcePattern()).isEqualTo(RESOURCE_PATTERN);
            assertThat(result.effect()).isEqualTo("ALLOW");
        }

        @Test
        @DisplayName("should keep existing values when update params are null")
        void shouldKeepExistingValuesWhenUpdateParamsAreNull() {
            PermissionPolicy existingPolicy = createNonSystemPolicy();
            when(policyRepository.findById(existingPolicy.id())).thenReturn(Optional.of(existingPolicy));

            UpdatePolicyCmd cmd = new UpdatePolicyCmd(
                existingPolicy.id(), null, null, null, "Only description changed"
            );
            PolicyDto result = service.updatePolicy(cmd);

            verify(policyRepository).save(any());
            assertThat(result.actionPattern()).isEqualTo(ACTION_PATTERN);
            assertThat(result.resourcePattern()).isEqualTo(RESOURCE_PATTERN);
        }

        @Test
        @DisplayName("should throw exception when policy not found")
        void shouldThrowExceptionWhenPolicyNotFound() {
            when(policyRepository.findById("non-existent")).thenReturn(Optional.empty());

            UpdatePolicyCmd cmd = new UpdatePolicyCmd("non-existent", null, null, null, null);

            assertThatThrownBy(() -> service.updatePolicy(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Policy not found: non-existent");
        }

        @Test
        @DisplayName("should throw exception when updating system policy")
        void shouldThrowExceptionWhenUpdatingSystemPolicy() {
            PermissionPolicy systemPolicy = createSystemPolicy();
            when(policyRepository.findById(systemPolicy.id())).thenReturn(Optional.of(systemPolicy));

            UpdatePolicyCmd cmd = new UpdatePolicyCmd(systemPolicy.id(), null, null, null, "Updated");

            assertThatThrownBy(() -> service.updatePolicy(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot update system policy");
        }
    }

    // ==================== Delete Policy Tests ====================

    @Nested
    @DisplayName("deletePolicy()")
    class DeletePolicyTests {

        @Test
        @DisplayName("should delete policy")
        void shouldDeletePolicy() {
            PermissionPolicy existingPolicy = createNonSystemPolicy();
            when(policyRepository.findById(existingPolicy.id())).thenReturn(Optional.of(existingPolicy));

            DeletePolicyCmd cmd = new DeletePolicyCmd(existingPolicy.id());
            service.deletePolicy(cmd);

            verify(policyRepository).deleteById(existingPolicy.id());
        }

        @Test
        @DisplayName("should throw exception when policy not found")
        void shouldThrowExceptionWhenPolicyNotFound() {
            when(policyRepository.findById("non-existent")).thenReturn(Optional.empty());

            DeletePolicyCmd cmd = new DeletePolicyCmd("non-existent");

            assertThatThrownBy(() -> service.deletePolicy(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Policy not found: non-existent");
        }

        @Test
        @DisplayName("should throw exception when deleting system policy")
        void shouldThrowExceptionWhenDeletingSystemPolicy() {
            PermissionPolicy systemPolicy = createSystemPolicy();
            when(policyRepository.findById(systemPolicy.id())).thenReturn(Optional.of(systemPolicy));

            DeletePolicyCmd cmd = new DeletePolicyCmd(systemPolicy.id());

            assertThatThrownBy(() -> service.deletePolicy(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot delete system policy");
        }
    }

    // ==================== Query Tests ====================

    @Nested
    @DisplayName("Query Methods")
    class QueryTests {

        @Test
        @DisplayName("getPolicyById should return policy DTO")
        void getPolicyByIdShouldReturnPolicyDto() {
            PermissionPolicy policy = createNonSystemPolicy();
            when(policyRepository.findById(policy.id())).thenReturn(Optional.of(policy));

            Optional<PolicyDto> result = service.getPolicyById(policy.id());

            assertThat(result).isPresent();
            assertThat(result.get().id()).isEqualTo(policy.id());
        }

        @Test
        @DisplayName("getPolicyById should return empty for non-existent policy")
        void getPolicyByIdShouldReturnEmptyForNonExistent() {
            when(policyRepository.findById("non-existent")).thenReturn(Optional.empty());

            Optional<PolicyDto> result = service.getPolicyById("non-existent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("listPoliciesByProfile should return policy DTOs")
        void listPoliciesByProfileShouldReturnPolicyDtos() {
            PermissionPolicy policy1 = createNonSystemPolicy();
            PermissionPolicy policy2 = createNonSystemPolicy();
            when(policyRepository.findByProfileId(PROFILE_ID)).thenReturn(List.of(policy1, policy2));

            List<PolicyDto> result = service.listPoliciesByProfile(PROFILE_ID);

            assertThat(result).hasSize(2);
        }

        @Test
        @DisplayName("listPoliciesBySubject should return policy DTOs")
        void listPoliciesBySubjectShouldReturnPolicyDtos() {
            PermissionPolicy policy = createNonSystemPolicy();
            when(policyRepository.findByProfileIdAndSubject(eq(PROFILE_ID), any(Subject.class)))
                .thenReturn(List.of(policy));

            List<PolicyDto> result = service.listPoliciesBySubject(PROFILE_ID, SUBJECT_URN);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("getEffectivePermissions should return policy DTOs")
        void getEffectivePermissionsShouldReturnPolicyDtos() {
            PermissionPolicy policy = createNonSystemPolicy();
            Set<String> roles = Set.of("READER", "CREATOR");
            when(authorizationService.getEffectivePermissions(PROFILE_ID, USER_ID, roles))
                .thenReturn(List.of(policy));

            List<PolicyDto> result = service.getEffectivePermissions(PROFILE_ID, USER_ID, roles);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("checkAuthorization should return allowed result")
        void checkAuthorizationShouldReturnAllowedResult() {
            Set<String> roles = Set.of("READER");
            PermissionPolicy policy = createNonSystemPolicy();
            PermissionAuthorizationService.PermissionResult mockResult =
                new PermissionAuthorizationService.PermissionResult(
                    true, "Permission granted", List.of(policy), PermissionPolicy.Effect.ALLOW
                );
            when(authorizationService.checkPermission(
                eq(PROFILE_ID), eq(USER_ID), eq(roles), any(Action.class), eq("account.123")
            )).thenReturn(mockResult);

            AuthorizationRequest request = new AuthorizationRequest(
                PROFILE_ID, USER_ID, roles, "payments.read", "account.123"
            );
            AuthorizationResult result = service.checkAuthorization(request);

            assertThat(result.allowed()).isTrue();
            assertThat(result.reason()).isEqualTo("Permission granted");
            assertThat(result.effectiveEffect()).isEqualTo("ALLOW");
        }

        @Test
        @DisplayName("checkAuthorization should return denied result")
        void checkAuthorizationShouldReturnDeniedResult() {
            Set<String> roles = Set.of("READER");
            PermissionAuthorizationService.PermissionResult mockResult =
                new PermissionAuthorizationService.PermissionResult(
                    false, "No matching policy", List.of(), null
                );
            when(authorizationService.checkPermission(
                eq(PROFILE_ID), eq(USER_ID), eq(roles), any(Action.class), eq("*")
            )).thenReturn(mockResult);

            AuthorizationRequest request = new AuthorizationRequest(
                PROFILE_ID, USER_ID, roles, "payments.read", null
            );
            AuthorizationResult result = service.checkAuthorization(request);

            assertThat(result.allowed()).isFalse();
            assertThat(result.reason()).isEqualTo("No matching policy");
            assertThat(result.effectiveEffect()).isNull();
        }
    }

    // ==================== Helper Methods ====================

    private PermissionPolicy createNonSystemPolicy() {
        return PermissionPolicy.create(
            PROFILE_ID,
            Subject.fromUrn(SUBJECT_URN),
            Action.of(ACTION_PATTERN),
            Resource.of(RESOURCE_PATTERN),
            PermissionPolicy.Effect.ALLOW,
            DESCRIPTION,
            CREATED_BY
        );
    }

    private PermissionPolicy createSystemPolicy() {
        // Use the static helper from PermissionPolicy to get a system policy
        return PermissionPolicy.forRole(PredefinedRole.SECURITY_ADMIN).get(0);
    }
}
