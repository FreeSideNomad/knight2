package com.knight.domain.policy.aggregate;

import com.knight.domain.policy.types.Action;
import com.knight.domain.policy.types.PredefinedRole;
import com.knight.domain.policy.types.Resource;
import com.knight.domain.policy.types.Subject;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("PermissionPolicy Tests")
class PermissionPolicyTest {

    private static final ProfileId TEST_PROFILE_ID = ProfileId.of("servicing", SrfClientId.of("srf:123456789"));
    private static final Subject TEST_SUBJECT = Subject.user(UUID.randomUUID().toString());
    private static final Action TEST_ACTION = Action.of("service.create");
    private static final String TEST_CREATOR = "test-user";

    @Nested
    @DisplayName("create() Tests")
    class CreateTests {

        @Test
        @DisplayName("should create policy with valid inputs")
        void shouldCreatePolicyWithValidInputs() {
            // When
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                TEST_ACTION,
                Resource.of("resource:123"),
                PermissionPolicy.Effect.ALLOW,
                "Test policy",
                TEST_CREATOR
            );

            // Then
            assertThat(policy).isNotNull();
            assertThat(policy.id()).isNotBlank();
            assertThat(policy.profileId()).isEqualTo(TEST_PROFILE_ID);
            assertThat(policy.subject()).isEqualTo(TEST_SUBJECT);
            assertThat(policy.action()).isEqualTo(TEST_ACTION);
            assertThat(policy.resource()).isEqualTo(Resource.of("resource:123"));
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
            assertThat(policy.description()).isEqualTo("Test policy");
            assertThat(policy.createdBy()).isEqualTo(TEST_CREATOR);
            assertThat(policy.createdAt()).isNotNull();
            assertThat(policy.updatedAt()).isNotNull();
            assertThat(policy.isSystemPolicy()).isFalse();
        }

        @Test
        @DisplayName("should throw exception when profileId is null")
        void shouldThrowExceptionWhenProfileIdIsNull() {
            // When/Then
            assertThatThrownBy(() -> PermissionPolicy.create(
                null,
                TEST_SUBJECT,
                TEST_ACTION,
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test policy",
                TEST_CREATOR
            ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("profileId is required");
        }

        @Test
        @DisplayName("should throw exception when createdBy is null")
        void shouldThrowExceptionWhenCreatedByIsNull() {
            // When/Then
            assertThatThrownBy(() -> PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                TEST_ACTION,
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test policy",
                null
            ))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("createdBy is required");
        }

        @Test
        @DisplayName("should default resource to all when null")
        void shouldDefaultResourceToAllWhenNull() {
            // When
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                TEST_ACTION,
                null,
                PermissionPolicy.Effect.ALLOW,
                "Test policy",
                TEST_CREATOR
            );

            // Then
            assertThat(policy.resource()).isEqualTo(Resource.all());
        }

        @Test
        @DisplayName("should default effect to ALLOW when null")
        void shouldDefaultEffectToAllowWhenNull() {
            // When
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                TEST_ACTION,
                Resource.all(),
                null,
                "Test policy",
                TEST_CREATOR
            );

            // Then
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
        }
    }

    @Nested
    @DisplayName("Predefined Role Factory Methods Tests")
    class PredefinedRoleTests {

        @Test
        @DisplayName("forSecurityAdmin() should create security.* policy")
        void forSecurityAdminShouldCreateSecurityPolicy() {
            // When
            List<PermissionPolicy> policies = PermissionPolicy.forSecurityAdmin();

            // Then
            assertThat(policies).hasSize(1);
            PermissionPolicy policy = policies.get(0);
            assertThat(policy.subject()).isEqualTo(Subject.role("SECURITY_ADMIN"));
            assertThat(policy.action()).isEqualTo(Action.of("security.*"));
            assertThat(policy.resource()).isEqualTo(Resource.all());
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
            assertThat(policy.isSystemPolicy()).isTrue();
            assertThat(policy.profileId()).isNull();
            assertThat(policy.createdBy()).isEqualTo("SYSTEM");
        }

        @Test
        @DisplayName("forServiceAdmin() should create * (all) policy")
        void forServiceAdminShouldCreateAllPolicy() {
            // When
            List<PermissionPolicy> policies = PermissionPolicy.forServiceAdmin();

            // Then
            assertThat(policies).hasSize(1);
            PermissionPolicy policy = policies.get(0);
            assertThat(policy.subject()).isEqualTo(Subject.role("SERVICE_ADMIN"));
            assertThat(policy.action()).isEqualTo(Action.of("*"));
            assertThat(policy.resource()).isEqualTo(Resource.all());
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
            assertThat(policy.isSystemPolicy()).isTrue();
        }

        @Test
        @DisplayName("forReader() should create *.view policy")
        void forReaderShouldCreateViewPolicy() {
            // When
            List<PermissionPolicy> policies = PermissionPolicy.forReader();

            // Then
            assertThat(policies).hasSize(1);
            PermissionPolicy policy = policies.get(0);
            assertThat(policy.subject()).isEqualTo(Subject.role("READER"));
            assertThat(policy.action()).isEqualTo(Action.of("*.view"));
            assertThat(policy.resource()).isEqualTo(Resource.all());
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
        }

        @Test
        @DisplayName("forCreator() should create *.create, *.update, *.delete policies")
        void forCreatorShouldCreateCrudPolicies() {
            // When
            List<PermissionPolicy> policies = PermissionPolicy.forCreator();

            // Then
            assertThat(policies).hasSize(3);
            assertThat(policies)
                .extracting(p -> p.action().value())
                .containsExactlyInAnyOrder("*.create", "*.update", "*.delete");
            assertThat(policies).allMatch(p -> p.subject().equals(Subject.role("CREATOR")));
            assertThat(policies).allMatch(p -> p.effect() == PermissionPolicy.Effect.ALLOW);
        }

        @Test
        @DisplayName("forApprover() should create *.approve policy")
        void forApproverShouldCreateApprovePolicy() {
            // When
            List<PermissionPolicy> policies = PermissionPolicy.forApprover();

            // Then
            assertThat(policies).hasSize(1);
            PermissionPolicy policy = policies.get(0);
            assertThat(policy.subject()).isEqualTo(Subject.role("APPROVER"));
            assertThat(policy.action()).isEqualTo(Action.of("*.approve"));
        }

        @Test
        @DisplayName("forRole() should return correct policies for each role")
        void forRoleShouldReturnCorrectPolicies() {
            // When/Then
            assertThat(PermissionPolicy.forRole(PredefinedRole.SECURITY_ADMIN))
                .hasSize(1)
                .first()
                .extracting(p -> p.action().value())
                .isEqualTo("security.*");

            assertThat(PermissionPolicy.forRole(PredefinedRole.SERVICE_ADMIN))
                .hasSize(1)
                .first()
                .extracting(p -> p.action().value())
                .isEqualTo("*");

            assertThat(PermissionPolicy.forRole(PredefinedRole.READER))
                .hasSize(1)
                .first()
                .extracting(p -> p.action().value())
                .isEqualTo("*.view");

            assertThat(PermissionPolicy.forRole(PredefinedRole.CREATOR))
                .hasSize(3);

            assertThat(PermissionPolicy.forRole(PredefinedRole.APPROVER))
                .hasSize(1)
                .first()
                .extracting(p -> p.action().value())
                .isEqualTo("*.approve");
        }

        @Test
        @DisplayName("forRoleName() should return policies for valid role name")
        void forRoleNameShouldReturnPolicies() {
            // When
            List<PermissionPolicy> policies = PermissionPolicy.forRoleName("SECURITY_ADMIN");

            // Then
            assertThat(policies).hasSize(1);
            assertThat(policies.get(0).action()).isEqualTo(Action.of("security.*"));
        }

        @Test
        @DisplayName("forRoleNames() should return policies for all predefined roles")
        void forRoleNamesShouldReturnPoliciesForAllRoles() {
            // When
            Set<String> roleNames = Set.of("SECURITY_ADMIN", "READER", "CUSTOM_ROLE");
            List<PermissionPolicy> policies = PermissionPolicy.forRoleNames(roleNames);

            // Then - Should only return policies for predefined roles (SECURITY_ADMIN and READER)
            assertThat(policies).hasSize(2);
        }

        @Test
        @DisplayName("forRoles() should return policies for multiple roles")
        void forRolesShouldReturnPoliciesForMultipleRoles() {
            // When
            Set<PredefinedRole> roles = Set.of(PredefinedRole.READER, PredefinedRole.CREATOR);
            List<PermissionPolicy> policies = PermissionPolicy.forRoles(roles);

            // Then - 1 for READER + 3 for CREATOR = 4 total
            assertThat(policies).hasSize(4);
        }
    }

    @Nested
    @DisplayName("matches() Tests")
    class MatchesTests {

        @Test
        @DisplayName("should match exact action and resource")
        void shouldMatchExactActionAndResource() {
            // Given
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                Action.of("service.create"),
                Resource.of("resource:123"),
                PermissionPolicy.Effect.ALLOW,
                "Test",
                TEST_CREATOR
            );

            // When/Then
            assertThat(policy.matches(Action.of("service.create"), "resource:123")).isTrue();
            assertThat(policy.matches(Action.of("service.delete"), "resource:123")).isFalse();
            assertThat(policy.matches(Action.of("service.create"), "resource:456")).isFalse();
        }

        @Test
        @DisplayName("should match wildcard action")
        void shouldMatchWildcardAction() {
            // Given
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                Action.of("*"),
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test",
                TEST_CREATOR
            );

            // When/Then
            assertThat(policy.matches(Action.of("service.create"), "resource:123")).isTrue();
            assertThat(policy.matches(Action.of("any.action"), "resource:456")).isTrue();
        }

        @Test
        @DisplayName("should match suffix wildcard action")
        void shouldMatchSuffixWildcardAction() {
            // Given
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                Action.of("*.create"),
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test",
                TEST_CREATOR
            );

            // When/Then
            assertThat(policy.matches(Action.of("service.create"), "any-resource")).isTrue();
            assertThat(policy.matches(Action.of("payment.create"), "any-resource")).isTrue();
            assertThat(policy.matches(Action.of("service.delete"), "any-resource")).isFalse();
        }

        @Test
        @DisplayName("should match prefix wildcard action")
        void shouldMatchPrefixWildcardAction() {
            // Given
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                Action.of("service.*"),
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test",
                TEST_CREATOR
            );

            // When/Then
            assertThat(policy.matches(Action.of("service.create"), "any-resource")).isTrue();
            assertThat(policy.matches(Action.of("service.delete"), "any-resource")).isTrue();
            assertThat(policy.matches(Action.of("payment.create"), "any-resource")).isFalse();
        }

        @Test
        @DisplayName("should match wildcard resource")
        void shouldMatchWildcardResource() {
            // Given
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                Action.of("service.create"),
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test",
                TEST_CREATOR
            );

            // When/Then
            assertThat(policy.matches(Action.of("service.create"), "any-resource")).isTrue();
            assertThat(policy.matches(Action.of("service.create"), "another-resource")).isTrue();
        }
    }

    @Nested
    @DisplayName("appliesTo() and appliesToAny() Tests")
    class AppliesToTests {

        @Test
        @DisplayName("appliesTo() should match exact subject")
        void appliesToShouldMatchExactSubject() {
            // Given
            Subject subject = Subject.user(UUID.randomUUID().toString());
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                subject,
                TEST_ACTION,
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test",
                TEST_CREATOR
            );

            // When/Then
            assertThat(policy.appliesTo(subject)).isTrue();
            assertThat(policy.appliesTo(Subject.user(UUID.randomUUID().toString()))).isFalse();
        }

        @Test
        @DisplayName("appliesToAny() should match any subject in collection")
        void appliesToAnyShouldMatchAnySubject() {
            // Given
            Subject roleSubject = Subject.role("READER");
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                roleSubject,
                TEST_ACTION,
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test",
                TEST_CREATOR
            );

            List<Subject> subjects = List.of(
                Subject.user(UUID.randomUUID().toString()),
                Subject.role("READER"),
                Subject.role("CREATOR")
            );

            // When/Then
            assertThat(policy.appliesToAny(subjects)).isTrue();
        }

        @Test
        @DisplayName("appliesToAny() should return false when no subject matches")
        void appliesToAnyShouldReturnFalseWhenNoMatch() {
            // Given
            Subject roleSubject = Subject.role("APPROVER");
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                roleSubject,
                TEST_ACTION,
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Test",
                TEST_CREATOR
            );

            List<Subject> subjects = List.of(
                Subject.user(UUID.randomUUID().toString()),
                Subject.role("READER"),
                Subject.role("CREATOR")
            );

            // When/Then
            assertThat(policy.appliesToAny(subjects)).isFalse();
        }
    }

    @Nested
    @DisplayName("update() Tests")
    class UpdateTests {

        @Test
        @DisplayName("should update custom policy")
        void shouldUpdateCustomPolicy() {
            // Given
            PermissionPolicy policy = PermissionPolicy.create(
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                TEST_ACTION,
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "Original description",
                TEST_CREATOR
            );
            Instant originalUpdatedAt = policy.updatedAt();

            // When
            try {
                Thread.sleep(10); // Ensure time difference
            } catch (InterruptedException e) {
                // Ignore
            }
            policy.update(
                Action.of("service.delete"),
                Resource.of("resource:456"),
                PermissionPolicy.Effect.DENY,
                "Updated description"
            );

            // Then
            assertThat(policy.updatedAt()).isAfter(originalUpdatedAt);
        }

        @Test
        @DisplayName("should throw exception when updating system policy")
        void shouldThrowExceptionWhenUpdatingSystemPolicy() {
            // Given
            List<PermissionPolicy> policies = PermissionPolicy.forSecurityAdmin();
            PermissionPolicy systemPolicy = policies.get(0);

            // When/Then
            assertThatThrownBy(() -> systemPolicy.update(
                Action.of("new.action"),
                Resource.all(),
                PermissionPolicy.Effect.ALLOW,
                "New description"
            ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot update system policy");
        }
    }

    @Nested
    @DisplayName("reconstitute() Tests")
    class ReconstituteTests {

        @Test
        @DisplayName("should reconstitute policy from persistence")
        void shouldReconstitutePolicyFromPersistence() {
            // Given
            String id = UUID.randomUUID().toString();
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);

            // When
            PermissionPolicy policy = PermissionPolicy.reconstitute(
                id,
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                TEST_ACTION,
                Resource.of("resource:123"),
                PermissionPolicy.Effect.DENY,
                "Reconstituted policy",
                createdAt,
                TEST_CREATOR,
                updatedAt
            );

            // Then
            assertThat(policy.id()).isEqualTo(id);
            assertThat(policy.profileId()).isEqualTo(TEST_PROFILE_ID);
            assertThat(policy.subject()).isEqualTo(TEST_SUBJECT);
            assertThat(policy.action()).isEqualTo(TEST_ACTION);
            assertThat(policy.resource()).isEqualTo(Resource.of("resource:123"));
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.DENY);
            assertThat(policy.description()).isEqualTo("Reconstituted policy");
            assertThat(policy.createdBy()).isEqualTo(TEST_CREATOR);
            assertThat(policy.updatedAt()).isEqualTo(updatedAt);
            assertThat(policy.isSystemPolicy()).isFalse();
        }

        @Test
        @DisplayName("should handle null resource and effect in reconstitute")
        void shouldHandleNullResourceAndEffectInReconstitute() {
            // When
            PermissionPolicy policy = PermissionPolicy.reconstitute(
                UUID.randomUUID().toString(),
                TEST_PROFILE_ID,
                TEST_SUBJECT,
                TEST_ACTION,
                null,
                null,
                "Test",
                Instant.now(),
                TEST_CREATOR,
                Instant.now()
            );

            // Then
            assertThat(policy.resource()).isEqualTo(Resource.all());
            assertThat(policy.effect()).isEqualTo(PermissionPolicy.Effect.ALLOW);
        }
    }
}
