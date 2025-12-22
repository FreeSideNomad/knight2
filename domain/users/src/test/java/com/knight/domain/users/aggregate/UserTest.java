package com.knight.domain.users.aggregate;

import com.knight.domain.users.aggregate.User.IdentityProvider;
import com.knight.domain.users.aggregate.User.Role;
import com.knight.domain.users.aggregate.User.Status;
import com.knight.domain.users.aggregate.User.UserType;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DisplayName("User Aggregate Tests")
class UserTest {

    private static final String VALID_EMAIL = "test@example.com";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final ProfileId PROFILE_ID = ProfileId.of("servicing", ClientId.of("srf:123456789"));
    private static final Set<Role> VALID_ROLES = Set.of(Role.READER, Role.CREATOR);
    private static final String CREATED_BY = "admin@example.com";

    @Nested
    @DisplayName("User Creation Tests")
    class CreateTests {

        @Test
        @DisplayName("should create user with valid inputs")
        void shouldCreateUserWithValidInputs() {
            // when
            User user = User.create(
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                VALID_ROLES,
                CREATED_BY
            );

            // then
            assertThat(user).isNotNull();
            assertThat(user.id()).isNotNull();
            assertThat(user.email()).isEqualTo(VALID_EMAIL);
            assertThat(user.firstName()).isEqualTo(FIRST_NAME);
            assertThat(user.lastName()).isEqualTo(LAST_NAME);
            assertThat(user.userType()).isEqualTo(UserType.CLIENT_USER);
            assertThat(user.identityProvider()).isEqualTo(IdentityProvider.AUTH0);
            assertThat(user.profileId()).isEqualTo(PROFILE_ID);
            assertThat(user.roles()).containsExactlyInAnyOrder(Role.READER, Role.CREATOR);
            assertThat(user.createdBy()).isEqualTo(CREATED_BY);
            assertThat(user.status()).isEqualTo(Status.PENDING_CREATION);
            assertThat(user.passwordSet()).isFalse();
            assertThat(user.mfaEnrolled()).isFalse();
            assertThat(user.identityProviderUserId()).isNull();
            assertThat(user.createdAt()).isNotNull();
            assertThat(user.updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("should reject null email")
        void shouldRejectNullEmail() {
            // when/then
            assertThatThrownBy(() -> User.create(
                null,
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                VALID_ROLES,
                CREATED_BY
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Valid email is required");
        }

        @Test
        @DisplayName("should reject blank email")
        void shouldRejectBlankEmail() {
            // when/then
            assertThatThrownBy(() -> User.create(
                "   ",
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                VALID_ROLES,
                CREATED_BY
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Valid email is required");
        }

        @Test
        @DisplayName("should reject email without @ symbol")
        void shouldRejectEmailWithoutAtSymbol() {
            // when/then
            assertThatThrownBy(() -> User.create(
                "invalidEmail",
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                VALID_ROLES,
                CREATED_BY
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Valid email is required");
        }

        @Test
        @DisplayName("should reject null roles")
        void shouldRejectNullRoles() {
            // when/then
            assertThatThrownBy(() -> User.create(
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                null,
                CREATED_BY
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one role is required");
        }

        @Test
        @DisplayName("should reject empty roles")
        void shouldRejectEmptyRoles() {
            // when/then
            assertThatThrownBy(() -> User.create(
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                Set.of(),
                CREATED_BY
            ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one role is required");
        }
    }

    @Nested
    @DisplayName("Mark Provisioned Tests")
    class MarkProvisionedTests {

        @Test
        @DisplayName("should mark user as provisioned with valid IdP user ID")
        void shouldMarkUserAsProvisioned() {
            // given
            User user = createValidUser();
            String idpUserId = "auth0|123456";

            // when
            user.markProvisioned(idpUserId);

            // then
            assertThat(user.identityProviderUserId()).isEqualTo(idpUserId);
            assertThat(user.status()).isEqualTo(Status.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("should reject provisioning if not in PENDING_CREATION status")
        void shouldRejectProvisioningIfNotPendingCreation() {
            // given
            User user = createValidUser();
            user.markProvisioned("auth0|123456");

            // when/then
            assertThatThrownBy(() -> user.markProvisioned("auth0|789"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User must be in PENDING_CREATION status to be provisioned");
        }

        @Test
        @DisplayName("should reject null IdP user ID")
        void shouldRejectNullIdpUserId() {
            // given
            User user = createValidUser();

            // when/then
            assertThatThrownBy(() -> user.markProvisioned(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Update Onboarding Status Tests")
    class UpdateOnboardingStatusTests {

        @Test
        @DisplayName("should transition to ACTIVE when both password and MFA are set")
        void shouldTransitionToActiveWhenPasswordAndMfaSet() {
            // given
            User user = createProvisionedUser();

            // when
            user.updateOnboardingStatus(true, true);

            // then
            assertThat(user.passwordSet()).isTrue();
            assertThat(user.mfaEnrolled()).isTrue();
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
            assertThat(user.lastSyncedAt()).isNotNull();
        }

        @Test
        @DisplayName("should transition to PENDING_MFA when only password is set")
        void shouldTransitionToPendingMfaWhenOnlyPasswordSet() {
            // given
            User user = createProvisionedUser();

            // when
            user.updateOnboardingStatus(true, false);

            // then
            assertThat(user.passwordSet()).isTrue();
            assertThat(user.mfaEnrolled()).isFalse();
            assertThat(user.status()).isEqualTo(Status.PENDING_MFA);
        }

        @Test
        @DisplayName("should remain PENDING_VERIFICATION when neither password nor MFA is set")
        void shouldRemainPendingVerificationWhenNeitherSet() {
            // given
            User user = createProvisionedUser();

            // when
            user.updateOnboardingStatus(false, false);

            // then
            assertThat(user.passwordSet()).isFalse();
            assertThat(user.mfaEnrolled()).isFalse();
            assertThat(user.status()).isEqualTo(Status.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("should update from PENDING_MFA to ACTIVE when MFA is enrolled")
        void shouldUpdateFromPendingMfaToActiveWhenMfaEnrolled() {
            // given
            User user = createProvisionedUser();
            user.updateOnboardingStatus(true, false); // First set password only

            // when
            user.updateOnboardingStatus(true, true); // Now enroll MFA

            // then
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
        }
    }

    @Nested
    @DisplayName("Activate User Tests")
    class ActivateTests {

        @Test
        @DisplayName("should activate user from DEACTIVATED status")
        void shouldActivateUserFromDeactivated() {
            // given
            User user = createActiveUser();
            user.deactivate("test reason");

            // when
            user.activate();

            // then
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
            assertThat(user.deactivationReason()).isNull();
        }

        @Test
        @DisplayName("should activate user from PENDING_VERIFICATION status")
        void shouldActivateUserFromPendingVerification() {
            // given
            User user = createProvisionedUser();

            // when
            user.activate();

            // then
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
        }

        @Test
        @DisplayName("should be idempotent when already ACTIVE")
        void shouldBeIdempotentWhenAlreadyActive() {
            // given
            User user = createActiveUser();

            // when
            user.activate();

            // then
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
        }

        @Test
        @DisplayName("should reject activation of LOCKED user")
        void shouldRejectActivationOfLockedUser() {
            // given
            User user = createActiveUser();
            user.lock("security concern");

            // when/then
            assertThatThrownBy(() -> user.activate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot activate locked user. Unlock first.");
        }
    }

    @Nested
    @DisplayName("Deactivate User Tests")
    class DeactivateTests {

        @Test
        @DisplayName("should deactivate user with reason")
        void shouldDeactivateUserWithReason() {
            // given
            User user = createActiveUser();
            String reason = "User requested account closure";

            // when
            user.deactivate(reason);

            // then
            assertThat(user.status()).isEqualTo(Status.DEACTIVATED);
            assertThat(user.deactivationReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("should be idempotent when already DEACTIVATED")
        void shouldBeIdempotentWhenAlreadyDeactivated() {
            // given
            User user = createActiveUser();
            user.deactivate("first reason");

            // when
            user.deactivate("second reason");

            // then
            assertThat(user.status()).isEqualTo(Status.DEACTIVATED);
        }
    }

    @Nested
    @DisplayName("Lock User Tests")
    class LockTests {

        @Test
        @DisplayName("should lock user with reason")
        void shouldLockUserWithReason() {
            // given
            User user = createActiveUser();
            String reason = "Multiple failed login attempts";

            // when
            user.lock(reason);

            // then
            assertThat(user.status()).isEqualTo(Status.LOCKED);
            assertThat(user.lockReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("should reject lock without reason")
        void shouldRejectLockWithoutReason() {
            // given
            User user = createActiveUser();

            // when/then
            assertThatThrownBy(() -> user.lock(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lock reason is required");
        }

        @Test
        @DisplayName("should reject lock with blank reason")
        void shouldRejectLockWithBlankReason() {
            // given
            User user = createActiveUser();

            // when/then
            assertThatThrownBy(() -> user.lock("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lock reason is required");
        }

        @Test
        @DisplayName("should be idempotent when already LOCKED")
        void shouldBeIdempotentWhenAlreadyLocked() {
            // given
            User user = createActiveUser();
            user.lock("first reason");

            // when
            user.lock("second reason");

            // then
            assertThat(user.status()).isEqualTo(Status.LOCKED);
        }
    }

    @Nested
    @DisplayName("Unlock User Tests")
    class UnlockTests {

        @Test
        @DisplayName("should unlock locked user")
        void shouldUnlockLockedUser() {
            // given
            User user = createActiveUser();
            user.lock("test reason");

            // when
            user.unlock();

            // then
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
            assertThat(user.lockReason()).isNull();
        }

        @Test
        @DisplayName("should reject unlock if user is not locked")
        void shouldRejectUnlockIfNotLocked() {
            // given
            User user = createActiveUser();

            // when/then
            assertThatThrownBy(() -> user.unlock())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User is not locked");
        }
    }

    @Nested
    @DisplayName("Add Role Tests")
    class AddRoleTests {

        @Test
        @DisplayName("should add new role to user")
        void shouldAddNewRole() {
            // given
            User user = createValidUser();
            Role newRole = Role.APPROVER;

            // when
            user.addRole(newRole);

            // then
            assertThat(user.roles()).contains(newRole);
        }

        @Test
        @DisplayName("should be idempotent when role already exists")
        void shouldBeIdempotentWhenRoleExists() {
            // given
            User user = createValidUser();
            int initialRoleCount = user.roles().size();

            // when
            user.addRole(Role.READER); // Already exists

            // then
            assertThat(user.roles()).hasSize(initialRoleCount);
            assertThat(user.roles()).contains(Role.READER);
        }
    }

    @Nested
    @DisplayName("Remove Role Tests")
    class RemoveRoleTests {

        @Test
        @DisplayName("should remove role when user has multiple roles")
        void shouldRemoveRoleWhenMultipleRolesExist() {
            // given
            User user = createValidUser(); // Has READER and CREATOR

            // when
            user.removeRole(Role.READER);

            // then
            assertThat(user.roles()).doesNotContain(Role.READER);
            assertThat(user.roles()).contains(Role.CREATOR);
        }

        @Test
        @DisplayName("should reject removal of last role")
        void shouldRejectRemovalOfLastRole() {
            // given
            User user = User.create(
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                Set.of(Role.READER), // Only one role
                CREATED_BY
            );

            // when/then
            assertThatThrownBy(() -> user.removeRole(Role.READER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot remove last role. User must have at least one role.");
        }

        @Test
        @DisplayName("should be idempotent when role does not exist")
        void shouldBeIdempotentWhenRoleDoesNotExist() {
            // given
            User user = createValidUser();
            int initialRoleCount = user.roles().size();

            // when
            user.removeRole(Role.SECURITY_ADMIN); // Does not exist

            // then
            assertThat(user.roles()).hasSize(initialRoleCount);
        }
    }

    @Nested
    @DisplayName("Update Name Tests")
    class UpdateNameTests {

        @Test
        @DisplayName("should update user name")
        void shouldUpdateUserName() {
            // given
            User user = createValidUser();
            String newFirstName = "Jane";
            String newLastName = "Smith";

            // when
            user.updateName(newFirstName, newLastName);

            // then
            assertThat(user.firstName()).isEqualTo(newFirstName);
            assertThat(user.lastName()).isEqualTo(newLastName);
        }

        @Test
        @DisplayName("should allow null names")
        void shouldAllowNullNames() {
            // given
            User user = createValidUser();

            // when
            user.updateName(null, null);

            // then
            assertThat(user.firstName()).isNull();
            assertThat(user.lastName()).isNull();
        }
    }

    @Nested
    @DisplayName("Reconstitute Tests")
    class ReconstituteTests {

        @Test
        @DisplayName("should reconstitute user from persistence")
        void shouldReconstituteUserFromPersistence() {
            // given
            UserId userId = UserId.of(UUID.randomUUID().toString());
            String idpUserId = "auth0|123456";
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant updatedAt = Instant.now().minusSeconds(1800);
            Instant lastSyncedAt = Instant.now().minusSeconds(900);

            // when
            User user = User.reconstitute(
                userId,
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                UserType.INDIRECT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                Set.of(Role.READER, Role.SECURITY_ADMIN),
                idpUserId,
                true,
                true,
                lastSyncedAt,
                Status.ACTIVE,
                null,
                null,
                createdAt,
                CREATED_BY,
                updatedAt
            );

            // then
            assertThat(user.id()).isEqualTo(userId);
            assertThat(user.email()).isEqualTo(VALID_EMAIL);
            assertThat(user.firstName()).isEqualTo(FIRST_NAME);
            assertThat(user.lastName()).isEqualTo(LAST_NAME);
            assertThat(user.userType()).isEqualTo(UserType.INDIRECT_USER);
            assertThat(user.identityProvider()).isEqualTo(IdentityProvider.AUTH0);
            assertThat(user.profileId()).isEqualTo(PROFILE_ID);
            assertThat(user.roles()).containsExactlyInAnyOrder(Role.READER, Role.SECURITY_ADMIN);
            assertThat(user.identityProviderUserId()).isEqualTo(idpUserId);
            assertThat(user.passwordSet()).isTrue();
            assertThat(user.mfaEnrolled()).isTrue();
            assertThat(user.lastSyncedAt()).isEqualTo(lastSyncedAt);
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
            assertThat(user.lockReason()).isNull();
            assertThat(user.deactivationReason()).isNull();
            assertThat(user.createdBy()).isEqualTo(CREATED_BY);
            assertThat(user.updatedAt()).isEqualTo(updatedAt);
        }

        @Test
        @DisplayName("should reconstitute locked user with lock reason")
        void shouldReconstituteLockedUserWithLockReason() {
            // given
            UserId userId = UserId.of(UUID.randomUUID().toString());
            String lockReason = "Security violation";

            // when
            User user = User.reconstitute(
                userId,
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.AUTH0,
                PROFILE_ID,
                VALID_ROLES,
                "auth0|123",
                true,
                false,
                Instant.now(),
                Status.LOCKED,
                lockReason,
                null,
                Instant.now().minusSeconds(3600),
                CREATED_BY,
                Instant.now()
            );

            // then
            assertThat(user.status()).isEqualTo(Status.LOCKED);
            assertThat(user.lockReason()).isEqualTo(lockReason);
        }

        @Test
        @DisplayName("should reconstitute deactivated user with deactivation reason")
        void shouldReconstituteDeactivatedUserWithDeactivationReason() {
            // given
            UserId userId = UserId.of(UUID.randomUUID().toString());
            String deactivationReason = "User request";

            // when
            User user = User.reconstitute(
                userId,
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                UserType.CLIENT_USER,
                IdentityProvider.ANP,
                PROFILE_ID,
                VALID_ROLES,
                null,
                false,
                false,
                null,
                Status.DEACTIVATED,
                null,
                deactivationReason,
                Instant.now().minusSeconds(3600),
                CREATED_BY,
                Instant.now()
            );

            // then
            assertThat(user.status()).isEqualTo(Status.DEACTIVATED);
            assertThat(user.deactivationReason()).isEqualTo(deactivationReason);
        }
    }

    // ==================== Helper Methods ====================

    private static User createValidUser() {
        return User.create(
            VALID_EMAIL,
            FIRST_NAME,
            LAST_NAME,
            UserType.CLIENT_USER,
            IdentityProvider.AUTH0,
            PROFILE_ID,
            VALID_ROLES,
            CREATED_BY
        );
    }

    private static User createProvisionedUser() {
        User user = createValidUser();
        user.markProvisioned("auth0|123456");
        return user;
    }

    private static User createActiveUser() {
        User user = createProvisionedUser();
        user.updateOnboardingStatus(true, true);
        return user;
    }
}
