package com.knight.domain.users.service;

import com.knight.domain.auth0identity.api.Auth0IdentityService;
import com.knight.domain.auth0identity.api.Auth0IdentityService.ProvisionUserRequest;
import com.knight.domain.auth0identity.api.Auth0IdentityService.ProvisionUserResult;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.aggregate.User.IdentityProvider;
import com.knight.domain.users.aggregate.User.Role;
import com.knight.domain.users.aggregate.User.Status;
import com.knight.domain.users.aggregate.User.UserType;
import com.knight.domain.users.api.commands.UserCommands.*;
import com.knight.domain.users.api.events.UserCreated;
import com.knight.domain.users.api.queries.UserQueries.*;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.ClientId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserApplicationService Tests")
class UserApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Auth0IdentityService auth0IdentityService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserApplicationService service;

    private static final String VALID_EMAIL = "test@example.com";
    private static final String FIRST_NAME = "John";
    private static final String LAST_NAME = "Doe";
    private static final ProfileId PROFILE_ID = ProfileId.of("servicing", ClientId.of("srf:123456789"));
    private static final Set<String> VALID_ROLES = Set.of("READER", "CREATOR");
    private static final String CREATED_BY = "admin@example.com";

    @Nested
    @DisplayName("Create User Tests")
    class CreateUserTests {

        @Test
        @DisplayName("should create user successfully")
        void shouldCreateUserSuccessfully() {
            // given
            CreateUserCmd cmd = new CreateUserCmd(
                "jdoe",
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                "CLIENT_USER",
                "AUTH0",
                PROFILE_ID,
                VALID_ROLES,
                CREATED_BY
            );

            when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(false);

            // when
            UserId userId = service.createUser(cmd);

            // then
            assertThat(userId).isNotNull();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());

            User savedUser = userCaptor.getValue();
            assertThat(savedUser.email()).isEqualTo(VALID_EMAIL);
            assertThat(savedUser.firstName()).isEqualTo(FIRST_NAME);
            assertThat(savedUser.lastName()).isEqualTo(LAST_NAME);
            assertThat(savedUser.userType()).isEqualTo(UserType.CLIENT_USER);
            assertThat(savedUser.identityProvider()).isEqualTo(IdentityProvider.AUTH0);
            assertThat(savedUser.profileId()).isEqualTo(PROFILE_ID);
            assertThat(savedUser.roles()).containsExactlyInAnyOrder(Role.READER, Role.CREATOR);

            ArgumentCaptor<UserCreated> eventCaptor = ArgumentCaptor.forClass(UserCreated.class);
            verify(eventPublisher).publishEvent(eventCaptor.capture());

            UserCreated event = eventCaptor.getValue();
            assertThat(event.email()).isEqualTo(VALID_EMAIL);
            assertThat(event.firstName()).isEqualTo(FIRST_NAME);
        }

        @Test
        @DisplayName("should reject duplicate email")
        void shouldRejectDuplicateEmail() {
            // given
            CreateUserCmd cmd = new CreateUserCmd(
                "jdoe",
                VALID_EMAIL,
                FIRST_NAME,
                LAST_NAME,
                "CLIENT_USER",
                "AUTH0",
                PROFILE_ID,
                VALID_ROLES,
                CREATED_BY
            );

            when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(true);

            // when/then
            assertThatThrownBy(() -> service.createUser(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User with email already exists");

            verify(userRepository, never()).save(any());
            verify(eventPublisher, never()).publishEvent(any());
        }
    }

    @Nested
    @DisplayName("Provision User Tests")
    class ProvisionUserTests {

        @Test
        @DisplayName("should provision AUTH0 user successfully")
        void shouldProvisionAuth0UserSuccessfully() {
            // given
            User user = createPendingUser(IdentityProvider.AUTH0);
            UserId userId = user.id();

            ProvisionUserCmd cmd = new ProvisionUserCmd(userId);

            String idpUserId = "auth0|123456";
            String passwordResetUrl = "https://auth0.com/reset?token=abc";
            ProvisionUserResult provisionResult = new ProvisionUserResult(
                idpUserId,
                passwordResetUrl,
                Instant.now()
            );

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auth0IdentityService.provisionUser(any(ProvisionUserRequest.class)))
                .thenReturn(provisionResult);

            // when
            ProvisionResult result = service.provisionUser(cmd);

            // then
            assertThat(result.identityProviderUserId()).isEqualTo(idpUserId);
            assertThat(result.passwordResetUrl()).isEqualTo(passwordResetUrl);

            verify(auth0IdentityService).provisionUser(any(ProvisionUserRequest.class));
            verify(userRepository).save(user);

            assertThat(user.identityProviderUserId()).isEqualTo(idpUserId);
            assertThat(user.status()).isEqualTo(Status.PENDING_VERIFICATION);
        }

        @Test
        @DisplayName("should reject provisioning non-AUTH0 user")
        void shouldRejectProvisioningNonAuth0User() {
            // given
            User user = createPendingUser(IdentityProvider.ANP);
            UserId userId = user.id();

            ProvisionUserCmd cmd = new ProvisionUserCmd(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when/then
            assertThatThrownBy(() -> service.provisionUser(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only AUTH0 users can be provisioned");

            verify(auth0IdentityService, never()).provisionUser(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("should reject provisioning already provisioned user")
        void shouldRejectProvisioningAlreadyProvisionedUser() {
            // given
            User user = createProvisionedUser();
            UserId userId = user.id();

            ProvisionUserCmd cmd = new ProvisionUserCmd(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when/then
            assertThatThrownBy(() -> service.provisionUser(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User already provisioned");

            verify(auth0IdentityService, never()).provisionUser(any());
        }

        @Test
        @DisplayName("should reject provisioning non-existent user")
        void shouldRejectProvisioningNonExistentUser() {
            // given
            UserId userId = UserId.of(UUID.randomUUID().toString());
            ProvisionUserCmd cmd = new ProvisionUserCmd(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.provisionUser(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("Update Onboarding Status Tests")
    class UpdateOnboardingStatusTests {

        @Test
        @DisplayName("should update onboarding status successfully")
        void shouldUpdateOnboardingStatusSuccessfully() {
            // given
            User user = createProvisionedUser();
            String idpUserId = user.identityProviderUserId();

            UpdateOnboardingStatusCmd cmd = new UpdateOnboardingStatusCmd(idpUserId, true, true);

            when(userRepository.findByIdentityProviderUserId(idpUserId)).thenReturn(Optional.of(user));

            // when
            service.updateOnboardingStatus(cmd);

            // then
            verify(userRepository).save(user);
            assertThat(user.passwordSet()).isTrue();
            assertThat(user.mfaEnrolled()).isTrue();
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
        }

        @Test
        @DisplayName("should reject update for non-existent IdP user ID")
        void shouldRejectUpdateForNonExistentIdpUserId() {
            // given
            String idpUserId = "auth0|nonexistent";
            UpdateOnboardingStatusCmd cmd = new UpdateOnboardingStatusCmd(idpUserId, true, false);

            when(userRepository.findByIdentityProviderUserId(idpUserId)).thenReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.updateOnboardingStatus(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found for IdP user ID");
        }
    }

    @Nested
    @DisplayName("Resend Invitation Tests")
    class ResendInvitationTests {

        @Test
        @DisplayName("should resend invitation successfully")
        void shouldResendInvitationSuccessfully() {
            // given
            User user = createProvisionedUser();
            UserId userId = user.id();
            String newPasswordResetUrl = "https://auth0.com/reset?token=new";

            ResendInvitationCmd cmd = new ResendInvitationCmd(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(auth0IdentityService.resendPasswordResetEmail(user.identityProviderUserId()))
                .thenReturn(newPasswordResetUrl);

            // when
            String result = service.resendInvitation(cmd);

            // then
            assertThat(result).isEqualTo(newPasswordResetUrl);
            verify(auth0IdentityService).resendPasswordResetEmail(user.identityProviderUserId());
        }

        @Test
        @DisplayName("should reject resend for non-provisioned user")
        void shouldRejectResendForNonProvisionedUser() {
            // given
            User user = createPendingUser(IdentityProvider.AUTH0);
            UserId userId = user.id();

            ResendInvitationCmd cmd = new ResendInvitationCmd(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when/then
            assertThatThrownBy(() -> service.resendInvitation(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User not yet provisioned to identity provider");
        }

        @Test
        @DisplayName("should reject resend for non-pending verification user")
        void shouldRejectResendForNonPendingVerificationUser() {
            // given
            User user = createActiveUser();
            UserId userId = user.id();

            ResendInvitationCmd cmd = new ResendInvitationCmd(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when/then
            assertThatThrownBy(() -> service.resendInvitation(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("User is not pending verification");
        }
    }

    @Nested
    @DisplayName("Activate User Tests")
    class ActivateUserTests {

        @Test
        @DisplayName("should activate user successfully")
        void shouldActivateUserSuccessfully() {
            // given
            User user = createDeactivatedUser();
            UserId userId = user.id();

            ActivateUserCmd cmd = new ActivateUserCmd(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.activateUser(cmd);

            // then
            verify(userRepository).save(user);
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
        }

        @Test
        @DisplayName("should reject activation for non-existent user")
        void shouldRejectActivationForNonExistentUser() {
            // given
            UserId userId = UserId.of(UUID.randomUUID().toString());
            ActivateUserCmd cmd = new ActivateUserCmd(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.activateUser(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("Deactivate User Tests")
    class DeactivateUserTests {

        @Test
        @DisplayName("should deactivate user and block in Auth0")
        void shouldDeactivateUserAndBlockInAuth0() {
            // given
            User user = createActiveUser();
            UserId userId = user.id();
            String reason = "User requested account closure";

            DeactivateUserCmd cmd = new DeactivateUserCmd(userId, reason);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.deactivateUser(cmd);

            // then
            verify(userRepository).save(user);
            verify(auth0IdentityService).blockUser(user.identityProviderUserId());
            assertThat(user.status()).isEqualTo(Status.DEACTIVATED);
            assertThat(user.deactivationReason()).isEqualTo(reason);
        }

        @Test
        @DisplayName("should deactivate user without Auth0 call if not provisioned")
        void shouldDeactivateUserWithoutAuth0CallIfNotProvisioned() {
            // given
            User user = createPendingUser(IdentityProvider.AUTH0);
            UserId userId = user.id();
            String reason = "Admin action";

            DeactivateUserCmd cmd = new DeactivateUserCmd(userId, reason);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.deactivateUser(cmd);

            // then
            verify(userRepository).save(user);
            verify(auth0IdentityService, never()).blockUser(any());
            assertThat(user.status()).isEqualTo(Status.DEACTIVATED);
        }
    }

    @Nested
    @DisplayName("Lock User Tests")
    class LockUserTests {

        @Test
        @DisplayName("should lock user and block in Auth0")
        void shouldLockUserAndBlockInAuth0() {
            // given
            User user = createActiveUser();
            UserId userId = user.id();
            String actor = "admin@example.com";

            LockUserCmd cmd = new LockUserCmd(userId, "CLIENT", actor);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.lockUser(cmd);

            // then
            verify(userRepository).save(user);
            verify(auth0IdentityService).blockUser(user.identityProviderUserId());
            assertThat(user.status()).isEqualTo(Status.LOCKED);
            assertThat(user.lockType()).isEqualTo(User.LockType.CLIENT);
            assertThat(user.lockedBy()).isEqualTo(actor);
        }

        @Test
        @DisplayName("should lock user without Auth0 call if not provisioned")
        void shouldLockUserWithoutAuth0CallIfNotProvisioned() {
            // given
            User user = createPendingUser(IdentityProvider.AUTH0);
            UserId userId = user.id();
            String actor = "security@example.com";

            LockUserCmd cmd = new LockUserCmd(userId, "SECURITY", actor);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.lockUser(cmd);

            // then
            verify(userRepository).save(user);
            verify(auth0IdentityService, never()).blockUser(any());
        }
    }

    @Nested
    @DisplayName("Unlock User Tests")
    class UnlockUserTests {

        @Test
        @DisplayName("should unlock user and unblock in Auth0")
        void shouldUnlockUserAndUnblockInAuth0() {
            // given
            User user = createLockedUser();
            UserId userId = user.id();
            String actor = "admin@example.com";

            UnlockUserCmd cmd = new UnlockUserCmd(userId, "CLIENT", actor);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.unlockUser(cmd);

            // then
            verify(userRepository).save(user);
            verify(auth0IdentityService).unblockUser(user.identityProviderUserId());
            assertThat(user.status()).isEqualTo(Status.ACTIVE);
            assertThat(user.lockType()).isEqualTo(User.LockType.NONE);
            assertThat(user.lockedBy()).isNull();
        }

        @Test
        @DisplayName("should unlock user without Auth0 call if not provisioned")
        void shouldUnlockUserWithoutAuth0CallIfNotProvisioned() {
            // given
            User user = createPendingUser(IdentityProvider.AUTH0);
            user.lock(User.LockType.CLIENT, "test-actor");
            UserId userId = user.id();
            String actor = "admin@example.com";

            UnlockUserCmd cmd = new UnlockUserCmd(userId, "CLIENT", actor);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.unlockUser(cmd);

            // then
            verify(userRepository).save(user);
            verify(auth0IdentityService, never()).unblockUser(any());
        }
    }

    @Nested
    @DisplayName("Add Role Tests")
    class AddRoleTests {

        @Test
        @DisplayName("should add role successfully")
        void shouldAddRoleSuccessfully() {
            // given
            User user = createActiveUser();
            UserId userId = user.id();

            AddRoleCmd cmd = new AddRoleCmd(userId, "SECURITY_ADMIN");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.addRole(cmd);

            // then
            verify(userRepository).save(user);
            assertThat(user.roles()).contains(Role.SECURITY_ADMIN);
        }

        @Test
        @DisplayName("should reject adding role to non-existent user")
        void shouldRejectAddingRoleToNonExistentUser() {
            // given
            UserId userId = UserId.of(UUID.randomUUID().toString());
            AddRoleCmd cmd = new AddRoleCmd(userId, "SECURITY_ADMIN");

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.addRole(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("Remove Role Tests")
    class RemoveRoleTests {

        @Test
        @DisplayName("should remove role successfully")
        void shouldRemoveRoleSuccessfully() {
            // given
            User user = createActiveUser();
            UserId userId = user.id();

            RemoveRoleCmd cmd = new RemoveRoleCmd(userId, "READER");

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.removeRole(cmd);

            // then
            verify(userRepository).save(user);
            assertThat(user.roles()).doesNotContain(Role.READER);
        }

        @Test
        @DisplayName("should reject removing role from non-existent user")
        void shouldRejectRemovingRoleFromNonExistentUser() {
            // given
            UserId userId = UserId.of(UUID.randomUUID().toString());
            RemoveRoleCmd cmd = new RemoveRoleCmd(userId, "READER");

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.removeRole(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }
    }

    @Nested
    @DisplayName("Update User Name Tests")
    class UpdateUserNameTests {

        @Test
        @DisplayName("should update user name successfully")
        void shouldUpdateUserNameSuccessfully() {
            // given
            User user = createActiveUser();
            UserId userId = user.id();
            String newFirstName = "Jane";
            String newLastName = "Smith";

            UpdateUserNameCmd cmd = new UpdateUserNameCmd(userId, newFirstName, newLastName);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            service.updateUserName(cmd);

            // then
            verify(userRepository).save(user);
            assertThat(user.firstName()).isEqualTo(newFirstName);
            assertThat(user.lastName()).isEqualTo(newLastName);
        }
    }

    @Nested
    @DisplayName("Query Methods Tests")
    class QueryMethodsTests {

        @Test
        @DisplayName("should list users by profile")
        void shouldListUsersByProfile() {
            // given
            User user1 = createActiveUser();
            User user2 = createProvisionedUser();

            when(userRepository.findByProfileId(PROFILE_ID)).thenReturn(List.of(user1, user2));

            // when
            List<ProfileUserSummary> results = service.listUsersByProfile(PROFILE_ID);

            // then
            assertThat(results).hasSize(2);
            assertThat(results.get(0).email()).isEqualTo(user1.email());
            assertThat(results.get(1).email()).isEqualTo(user2.email());
        }

        @Test
        @DisplayName("should count users by status for profile")
        void shouldCountUsersByStatusForProfile() {
            // given
            User activeUser = createActiveUser();
            User pendingUser = createProvisionedUser();

            when(userRepository.findByProfileId(PROFILE_ID)).thenReturn(List.of(activeUser, pendingUser));

            // when
            Map<String, Integer> counts = service.countUsersByStatusForProfile(PROFILE_ID);

            // then
            assertThat(counts).hasSize(2);
            assertThat(counts.get("ACTIVE")).isEqualTo(1);
            assertThat(counts.get("PENDING_VERIFICATION")).isEqualTo(1);
        }

        @Test
        @DisplayName("should get user detail")
        void shouldGetUserDetail() {
            // given
            User user = createActiveUser();
            UserId userId = user.id();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserDetail detail = service.getUserDetail(userId);

            // then
            assertThat(detail.userId()).isEqualTo(userId.id());
            assertThat(detail.email()).isEqualTo(user.email());
            assertThat(detail.status()).isEqualTo("ACTIVE");
            assertThat(detail.passwordSet()).isTrue();
            assertThat(detail.mfaEnrolled()).isTrue();
        }

        @Test
        @DisplayName("should get user summary")
        void shouldGetUserSummary() {
            // given
            User user = createActiveUser();
            UserId userId = user.id();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            UserSummary summary = service.getUserSummary(userId);

            // then
            assertThat(summary.userId()).isEqualTo(userId.id());
            assertThat(summary.email()).isEqualTo(user.email());
            assertThat(summary.status()).isEqualTo("ACTIVE");
        }

        @Test
        @DisplayName("should find by identity provider user ID")
        void shouldFindByIdentityProviderUserId() {
            // given
            User user = createActiveUser();
            String idpUserId = user.identityProviderUserId();

            when(userRepository.findByIdentityProviderUserId(idpUserId)).thenReturn(Optional.of(user));

            // when
            UserDetail detail = service.findByIdentityProviderUserId(idpUserId);

            // then
            assertThat(detail.identityProviderUserId()).isEqualTo(idpUserId);
        }

        @Test
        @DisplayName("should find by email")
        void shouldFindByEmail() {
            // given
            User user = createActiveUser();

            when(userRepository.findByEmail(VALID_EMAIL)).thenReturn(Optional.of(user));

            // when
            UserDetail detail = service.findByEmail(VALID_EMAIL);

            // then
            assertThat(detail.email()).isEqualTo(VALID_EMAIL);
        }

        @Test
        @DisplayName("should check if email exists")
        void shouldCheckIfEmailExists() {
            // given
            when(userRepository.existsByEmail(VALID_EMAIL)).thenReturn(true);

            // when
            boolean exists = service.existsByEmail(VALID_EMAIL);

            // then
            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("should throw exception for non-existent user in getUserDetail")
        void shouldThrowExceptionForNonExistentUserInGetUserDetail() {
            // given
            UserId userId = UserId.of(UUID.randomUUID().toString());
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when/then
            assertThatThrownBy(() -> service.getUserDetail(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
        }
    }

    // ==================== Helper Methods ====================

    private User createPendingUser(IdentityProvider identityProvider) {
        return User.create(
            "testuser",
            VALID_EMAIL,
            FIRST_NAME,
            LAST_NAME,
            UserType.CLIENT_USER,
            identityProvider,
            PROFILE_ID,
            Set.of(Role.READER, Role.CREATOR),
            CREATED_BY
        );
    }

    private User createProvisionedUser() {
        User user = createPendingUser(IdentityProvider.AUTH0);
        user.markProvisioned("auth0|123456");
        return user;
    }

    private User createActiveUser() {
        User user = createProvisionedUser();
        user.updateOnboardingStatus(true, true);
        return user;
    }

    private User createDeactivatedUser() {
        User user = createActiveUser();
        user.deactivate("test reason");
        return user;
    }

    private User createLockedUser() {
        User user = createActiveUser();
        user.lock(User.LockType.CLIENT, "test-actor");
        return user;
    }
}
