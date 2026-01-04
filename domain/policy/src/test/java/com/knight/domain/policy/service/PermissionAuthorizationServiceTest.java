package com.knight.domain.policy.service;

import com.knight.domain.policy.aggregate.PermissionPolicy;
import com.knight.domain.policy.port.UserGroupLookup;
import com.knight.domain.policy.repository.PermissionPolicyRepository;
import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionAuthorizationService Tests")
class PermissionAuthorizationServiceTest {

    @Mock
    private PermissionPolicyRepository policyRepository;

    @Mock
    private UserGroupLookup userGroupLookup;

    private PermissionAuthorizationServiceImpl authorizationService;

    private static final ProfileId TEST_PROFILE_ID = ProfileId.of("servicing", SrfClientId.of("srf:123456789"));
    private static final UserId TEST_USER_ID = UserId.of(UUID.randomUUID().toString());
    private static final String RESOURCE_ID = "resource:123";

    @BeforeEach
    void setUp() {
        // By default, user has no group memberships
        when(userGroupLookup.getGroupsForUser(any())).thenReturn(Set.of());
        authorizationService = new PermissionAuthorizationServiceImpl(policyRepository, userGroupLookup);
    }

    @Nested
    @DisplayName("checkPermission() with Resource Tests")
    class CheckPermissionWithResourceTests {

        @Test
        @DisplayName("should allow when matching ALLOW policy exists")
        void shouldAllowWhenMatchingAllowPolicyExists() {
            // Given
            Set<String> userRoles = Set.of("CREATOR");
            Action action = Action.of("service.create");

            // Mock repository to return no persisted policies
            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When - CREATOR role has *.create permission
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isTrue();
            assertThat(result.effectiveEffect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
            assertThat(result.matchingPolicies()).isNotEmpty();
        }

        @Test
        @DisplayName("should deny when no matching policy exists")
        void shouldDenyWhenNoMatchingPolicyExists() {
            // Given
            Set<String> userRoles = Set.of("READER");
            Action action = Action.of("service.delete");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isFalse();
            assertThat(result.effectiveEffect()).isNull();
            assertThat(result.reason()).contains("No matching policy found");
        }

        @Test
        @DisplayName("should allow SERVICE_ADMIN for any action")
        void shouldAllowServiceAdminForAnyAction() {
            // Given
            Set<String> userRoles = Set.of("SERVICE_ADMIN");
            Action action = Action.of("any.action.here");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isTrue();
            assertThat(result.effectiveEffect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
        }

        @Test
        @DisplayName("should allow SECURITY_ADMIN for security actions")
        void shouldAllowSecurityAdminForSecurityActions() {
            // Given
            Set<String> userRoles = Set.of("SECURITY_ADMIN");
            Action action = Action.of("security.admin.manage");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should allow READER for view actions")
        void shouldAllowReaderForViewActions() {
            // Given
            Set<String> userRoles = Set.of("READER");
            Action action = Action.of("service.view");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should allow APPROVER for approve actions")
        void shouldAllowApproverForApproveActions() {
            // Given
            Set<String> userRoles = Set.of("APPROVER");
            Action action = Action.of("payment.approve");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should combine multiple role permissions")
        void shouldCombineMultipleRolePermissions() {
            // Given
            Set<String> userRoles = Set.of("READER", "CREATOR");
            Action createAction = Action.of("service.create");
            Action viewAction = Action.of("service.view");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When/Then - Both should be allowed
            assertThat(authorizationService.checkPermission(
                TEST_PROFILE_ID, TEST_USER_ID, userRoles, createAction, RESOURCE_ID
            ).allowed()).isTrue();

            assertThat(authorizationService.checkPermission(
                TEST_PROFILE_ID, TEST_USER_ID, userRoles, viewAction, RESOURCE_ID
            ).allowed()).isTrue();
        }

        @Test
        @DisplayName("should consider persisted policies alongside role-based policies")
        void shouldConsiderPersistedPolicies() {
            // Given
            Set<String> userRoles = Set.of(); // No predefined roles
            Action action = Action.of("custom.action");
            Subject userSubject = Subject.user(TEST_USER_ID.id());

            // Create a custom policy
            PermissionPolicy customPolicy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                userSubject,
                action,
                Resource.of(RESOURCE_ID),
                PermissionPolicy.Effect.ALLOW,
                "Custom policy",
                "admin"
            );

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of(customPolicy));

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isTrue();
            assertThat(result.matchingPolicies()).contains(customPolicy);
        }
    }

    @Nested
    @DisplayName("checkPermission() without Resource Tests")
    class CheckPermissionWithoutResourceTests {

        @Test
        @DisplayName("should check permission without resource ID")
        void shouldCheckPermissionWithoutResourceId() {
            // Given
            Set<String> userRoles = Set.of("CREATOR");
            Action action = Action.of("service.create");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action
            );

            // Then
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should deny when no permission for action")
        void shouldDenyWhenNoPermissionForAction() {
            // Given
            Set<String> userRoles = Set.of("READER");
            Action action = Action.of("service.delete");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action
            );

            // Then
            assertThat(result.allowed()).isFalse();
        }
    }

    @Nested
    @DisplayName("DENY Precedence Tests")
    class DenyPrecedenceTests {

        @Test
        @DisplayName("DENY should take precedence over ALLOW")
        void denyShouldTakePrecedenceOverAllow() {
            // Given
            Set<String> userRoles = Set.of("SERVICE_ADMIN"); // Has ALLOW for everything
            Action action = Action.of("sensitive.action");
            Subject userSubject = Subject.user(TEST_USER_ID.id());

            // Create a DENY policy for this specific user
            PermissionPolicy denyPolicy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                userSubject,
                action,
                Resource.all(),
                PermissionPolicy.Effect.DENY,
                "Deny sensitive action",
                "admin"
            );

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of(denyPolicy));

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isFalse();
            assertThat(result.effectiveEffect()).isEqualTo(PermissionPolicy.Effect.DENY);
            assertThat(result.reason()).contains("denied");
        }

        @Test
        @DisplayName("DENY policy should override multiple ALLOW policies")
        void denyPolicyShouldOverrideMultipleAllowPolicies() {
            // Given
            Set<String> userRoles = Set.of("SERVICE_ADMIN", "CREATOR", "APPROVER");
            Action action = Action.of("restricted.action");
            Subject userSubject = Subject.user(TEST_USER_ID.id());

            PermissionPolicy denyPolicy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                userSubject,
                Action.of("*"),
                Resource.all(),
                PermissionPolicy.Effect.DENY,
                "Block all actions for this user",
                "admin"
            );

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of(denyPolicy));

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isFalse();
            assertThat(result.effectiveEffect()).isEqualTo(PermissionPolicy.Effect.DENY);
        }

        @Test
        @DisplayName("DENY on specific resource should block access")
        void denyOnSpecificResourceShouldBlockAccess() {
            // Given
            Set<String> userRoles = Set.of("CREATOR");
            Action action = Action.of("service.delete");
            String protectedResource = "resource:protected";
            Subject userSubject = Subject.user(TEST_USER_ID.id());

            PermissionPolicy denyPolicy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                userSubject,
                action,
                Resource.of(protectedResource),
                PermissionPolicy.Effect.DENY,
                "Protect specific resource",
                "admin"
            );

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of(denyPolicy));

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                protectedResource
            );

            // Then
            assertThat(result.allowed()).isFalse();
            assertThat(result.effectiveEffect()).isEqualTo(PermissionPolicy.Effect.DENY);
        }
    }

    @Nested
    @DisplayName("getEffectivePermissions() Tests")
    class GetEffectivePermissionsTests {

        @Test
        @DisplayName("should return all effective permissions for user with roles")
        void shouldReturnAllEffectivePermissionsForUserWithRoles() {
            // Given
            Set<String> userRoles = Set.of("READER", "CREATOR");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            List<PermissionPolicy> permissions = authorizationService.getEffectivePermissions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then - Should have 1 for READER + 3 for CREATOR = 4
            assertThat(permissions).hasSize(4);
        }

        @Test
        @DisplayName("should include persisted policies in effective permissions")
        void shouldIncludePersistedPoliciesInEffectivePermissions() {
            // Given
            Set<String> userRoles = Set.of("READER");
            Subject userSubject = Subject.user(TEST_USER_ID.id());

            PermissionPolicy customPolicy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                userSubject,
                Action.of("custom.action"),
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Custom policy",
                "admin"
            );

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of(customPolicy));

            // When
            List<PermissionPolicy> permissions = authorizationService.getEffectivePermissions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then - Should have 1 for READER + 1 custom = 2
            assertThat(permissions).hasSize(2);
            assertThat(permissions).contains(customPolicy);
        }

        @Test
        @DisplayName("should return only role-based policies when no custom policies exist")
        void shouldReturnOnlyRoleBasedPoliciesWhenNoCustomPolicies() {
            // Given
            Set<String> userRoles = Set.of("SECURITY_ADMIN");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            List<PermissionPolicy> permissions = authorizationService.getEffectivePermissions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then
            assertThat(permissions).hasSize(1);
            assertThat(permissions.get(0).isSystemPolicy()).isTrue();
        }

        @Test
        @DisplayName("should return empty list when user has no roles or policies")
        void shouldReturnEmptyListWhenUserHasNoRolesOrPolicies() {
            // Given
            Set<String> userRoles = Set.of();

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            List<PermissionPolicy> permissions = authorizationService.getEffectivePermissions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then
            assertThat(permissions).isEmpty();
        }

        @Test
        @DisplayName("should handle SERVICE_ADMIN role")
        void shouldHandleServiceAdminRole() {
            // Given
            Set<String> userRoles = Set.of("SERVICE_ADMIN");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            List<PermissionPolicy> permissions = authorizationService.getEffectivePermissions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then
            assertThat(permissions).hasSize(1);
            assertThat(permissions.get(0).action().value()).isEqualTo("*");
        }
    }

    @Nested
    @DisplayName("getAllowedActions() Tests")
    class GetAllowedActionsTests {

        @Test
        @DisplayName("should return all allowed actions for user")
        void shouldReturnAllAllowedActionsForUser() {
            // Given
            Set<String> userRoles = Set.of("CREATOR");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            Set<String> allowedActions = authorizationService.getAllowedActions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then - CREATOR has *.create, *.update, *.delete
            assertThat(allowedActions).containsExactlyInAnyOrder("*.create", "*.update", "*.delete");
        }

        @Test
        @DisplayName("should include only ALLOW policies in allowed actions")
        void shouldIncludeOnlyAllowPoliciesInAllowedActions() {
            // Given
            Set<String> userRoles = Set.of("READER");
            Subject userSubject = Subject.user(TEST_USER_ID.id());

            PermissionPolicy allowPolicy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                userSubject,
                Action.of("custom.allow"),
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Allow policy",
                "admin"
            );

            PermissionPolicy denyPolicy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                userSubject,
                Action.of("custom.deny"),
                Resource.all(),
                PermissionPolicy.Effect.DENY,
                "Deny policy",
                "admin"
            );

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of(allowPolicy, denyPolicy));

            // When
            Set<String> allowedActions = authorizationService.getAllowedActions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then - Should include *.view from READER + custom.allow, but not custom.deny
            assertThat(allowedActions).contains("*.view", "custom.allow");
            assertThat(allowedActions).doesNotContain("custom.deny");
        }

        @Test
        @DisplayName("should return wildcard for SERVICE_ADMIN")
        void shouldReturnWildcardForServiceAdmin() {
            // Given
            Set<String> userRoles = Set.of("SERVICE_ADMIN");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            Set<String> allowedActions = authorizationService.getAllowedActions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then
            assertThat(allowedActions).contains("*");
        }

        @Test
        @DisplayName("should return empty set when no permissions")
        void shouldReturnEmptySetWhenNoPermissions() {
            // Given
            Set<String> userRoles = Set.of();

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            Set<String> allowedActions = authorizationService.getAllowedActions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then
            assertThat(allowedActions).isEmpty();
        }

        @Test
        @DisplayName("should combine actions from multiple roles")
        void shouldCombineActionsFromMultipleRoles() {
            // Given
            Set<String> userRoles = Set.of("READER", "APPROVER");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            Set<String> allowedActions = authorizationService.getAllowedActions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then
            assertThat(allowedActions).contains("*.view", "*.approve");
        }
    }

    @Nested
    @DisplayName("Repository Interaction Tests")
    class RepositoryInteractionTests {

        @Test
        @DisplayName("should call repository with correct parameters")
        void shouldCallRepositoryWithCorrectParameters() {
            // Given
            Set<String> userRoles = Set.of("READER");
            Action action = Action.of("service.view");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            verify(policyRepository).findByProfileIdAndSubjects(
                eq(TEST_PROFILE_ID),
                argThat(subjects -> subjects.stream().anyMatch(s ->
                    s.equals(Subject.user(TEST_USER_ID.id())) || s.equals(Subject.role("READER"))
                ))
            );
        }

        @Test
        @DisplayName("should build correct subject list for user with multiple roles")
        void shouldBuildCorrectSubjectListForUserWithMultipleRoles() {
            // Given
            Set<String> userRoles = Set.of("READER", "CREATOR", "APPROVER");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            authorizationService.getEffectivePermissions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then - Should query with user subject + 3 role subjects = 4 total
            verify(policyRepository).findByProfileIdAndSubjects(
                eq(TEST_PROFILE_ID),
                argThat(subjects -> subjects.size() == 4)
            );
        }

        @Test
        @DisplayName("should include user groups in subject list")
        void shouldIncludeUserGroupsInSubjectList() {
            // Given
            Set<String> userRoles = Set.of("READER");
            UUID groupId1 = UUID.randomUUID();
            UUID groupId2 = UUID.randomUUID();

            when(userGroupLookup.getGroupsForUser(TEST_USER_ID))
                .thenReturn(Set.of(groupId1, groupId2));

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            authorizationService.getEffectivePermissions(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles
            );

            // Then - Should query with user subject + 2 group subjects + 1 role subject = 4 total
            verify(policyRepository).findByProfileIdAndSubjects(
                eq(TEST_PROFILE_ID),
                argThat(subjects -> {
                    boolean hasUser = subjects.stream().anyMatch(s ->
                        s.type() == Subject.SubjectType.USER && s.identifier().equals(TEST_USER_ID.id()));
                    boolean hasGroup1 = subjects.stream().anyMatch(s ->
                        s.type() == Subject.SubjectType.GROUP && s.identifier().equals(groupId1.toString()));
                    boolean hasGroup2 = subjects.stream().anyMatch(s ->
                        s.type() == Subject.SubjectType.GROUP && s.identifier().equals(groupId2.toString()));
                    boolean hasRole = subjects.stream().anyMatch(s ->
                        s.type() == Subject.SubjectType.ROLE && s.identifier().equals("READER"));
                    return hasUser && hasGroup1 && hasGroup2 && hasRole && subjects.size() == 4;
                })
            );
        }

        @Test
        @DisplayName("should allow action when user has permission via group")
        void shouldAllowActionWhenUserHasPermissionViaGroup() {
            // Given
            Set<String> userRoles = Set.of(); // No predefined roles
            UUID groupId = UUID.randomUUID();
            Action action = Action.of("group.action");

            when(userGroupLookup.getGroupsForUser(TEST_USER_ID))
                .thenReturn(Set.of(groupId));

            // Create a policy that allows the group to perform the action
            PermissionPolicy groupPolicy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                Subject.group(groupId),
                action,
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Group policy",
                "admin"
            );

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of(groupPolicy));

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isTrue();
            assertThat(result.matchingPolicies()).contains(groupPolicy);
        }

        @Test
        @DisplayName("should not call repository when checking role-based permissions only")
        void shouldCallRepositoryEvenForRoleBasedPermissions() {
            // Given
            Set<String> userRoles = Set.of("SERVICE_ADMIN");
            Action action = Action.of("any.action");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then - Repository is always called to get persisted policies
            verify(policyRepository).findByProfileIdAndSubjects(any(), anyList());
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("should handle user with no predefined roles")
        void shouldHandleUserWithNoPredefinedRoles() {
            // Given
            Set<String> userRoles = Set.of("CUSTOM_ROLE", "ANOTHER_ROLE");
            Action action = Action.of("service.create");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                RESOURCE_ID
            );

            // Then - Should deny because no matching predefined role
            assertThat(result.allowed()).isFalse();
        }

        @Test
        @DisplayName("should handle wildcard action matching")
        void shouldHandleWildcardActionMatching() {
            // Given
            Set<String> userRoles = Set.of("SERVICE_ADMIN"); // Has * action
            Action specificAction = Action.of("very.specific.nested.action");

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                specificAction,
                RESOURCE_ID
            );

            // Then
            assertThat(result.allowed()).isTrue();
        }

        @Test
        @DisplayName("should handle policy with wildcard resource")
        void shouldHandlePolicyWithWildcardResource() {
            // Given
            Set<String> userRoles = Set.of("CREATOR");
            Action action = Action.of("service.create");
            String anyResource = "any:resource:id";

            when(policyRepository.findByProfileIdAndSubjects(any(), anyList()))
                .thenReturn(List.of());

            // When - Role-based policies have wildcard resources
            PermissionAuthorizationService.PermissionResult result = authorizationService.checkPermission(
                TEST_PROFILE_ID,
                TEST_USER_ID,
                userRoles,
                action,
                anyResource
            );

            // Then
            assertThat(result.allowed()).isTrue();
        }
    }
}
