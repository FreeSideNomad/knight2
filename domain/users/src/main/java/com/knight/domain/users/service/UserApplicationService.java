package com.knight.domain.users.service;

import com.knight.domain.auth0identity.api.Auth0IdentityService;
import com.knight.domain.users.aggregate.User;
import com.knight.domain.users.api.commands.UserCommands;
import com.knight.domain.users.api.events.UserCreated;
import com.knight.domain.users.api.events.UserEmailChanged;
import com.knight.domain.users.api.queries.UserQueries;
import com.knight.domain.users.repository.UserRepository;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Application service for User Management.
 * Orchestrates user lifecycle operations with transactions and event publishing.
 */
@Service
public class UserApplicationService implements UserCommands, UserQueries {

    private final UserRepository repository;
    private final Auth0IdentityService auth0IdentityService;
    private final ApplicationEventPublisher eventPublisher;

    public UserApplicationService(
        UserRepository repository,
        Auth0IdentityService auth0IdentityService,
        ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.auth0IdentityService = auth0IdentityService;
        this.eventPublisher = eventPublisher;
    }

    // ==================== User Creation ====================

    @Override
    @Transactional
    public UserId createUser(CreateUserCmd cmd) {
        // Check if email already exists
        if (repository.existsByEmail(cmd.email())) {
            throw new IllegalArgumentException("User with email already exists: " + cmd.email());
        }

        User.UserType userType = User.UserType.valueOf(cmd.userType());
        User.IdentityProvider identityProvider = User.IdentityProvider.valueOf(cmd.identityProvider());
        Set<User.Role> roles = cmd.roles().stream()
            .map(User.Role::valueOf)
            .collect(Collectors.toSet());

        User user = User.create(
            cmd.loginId(),
            cmd.email(),
            cmd.firstName(),
            cmd.lastName(),
            userType,
            identityProvider,
            cmd.profileId(),
            roles,
            cmd.createdBy()
        );

        repository.save(user);

        eventPublisher.publishEvent(new UserCreated(
            user.id().id(),
            cmd.loginId(),
            cmd.email(),
            cmd.firstName(),
            cmd.lastName(),
            cmd.userType(),
            cmd.identityProvider(),
            cmd.profileId().urn(),
            cmd.roles(),
            Instant.now()
        ));

        return user.id();
    }

    // ==================== Identity Provider Provisioning ====================

    @Override
    @Transactional
    public ProvisionResult provisionUser(ProvisionUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        if (user.identityProvider() != User.IdentityProvider.AUTH0) {
            throw new IllegalStateException("Only AUTH0 users can be provisioned. User has: " + user.identityProvider());
        }

        if (user.status() != User.Status.PENDING_CREATION) {
            throw new IllegalStateException("User already provisioned. Status: " + user.status());
        }

        // Call Auth0 to provision the user
        // loginId is used as Auth0 email field (login identifier)
        // email is real email stored locally for OTP delivery
        Auth0IdentityService.ProvisionUserResult result = auth0IdentityService.provisionUser(
            new Auth0IdentityService.ProvisionUserRequest(
                user.loginId(),   // Used as Auth0 email field
                user.email(),     // Real email for OTP (local DB only)
                user.firstName(),
                user.lastName(),
                user.id().id(),
                user.profileId().urn()
            )
        );

        // Update user with IdP info
        user.markProvisioned(result.identityProviderUserId());
        repository.save(user);

        return new ProvisionResult(result.identityProviderUserId(), result.passwordResetUrl());
    }

    @Override
    @Transactional
    public void updateOnboardingStatus(UpdateOnboardingStatusCmd cmd) {
        User user = repository.findByIdentityProviderUserId(cmd.identityProviderUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found for IdP user ID: " + cmd.identityProviderUserId()));

        user.updateOnboardingStatus(cmd.emailVerified(), cmd.passwordSet(), cmd.mfaEnrolled());
        repository.save(user);
    }

    @Override
    @Transactional
    public String resendInvitation(ResendInvitationCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        if (user.identityProviderUserId() == null) {
            throw new IllegalStateException("User not yet provisioned to identity provider");
        }

        if (user.status() != User.Status.PENDING_VERIFICATION) {
            throw new IllegalStateException("User is not pending verification. Status: " + user.status());
        }

        return auth0IdentityService.resendPasswordResetEmail(user.identityProviderUserId());
    }

    // ==================== User Lifecycle ====================

    @Override
    @Transactional
    public void activateUser(ActivateUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        user.activate();
        repository.save(user);
    }

    @Override
    @Transactional
    public void deactivateUser(DeactivateUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        user.deactivate(cmd.reason());
        repository.save(user);

        // Block user in Auth0 if provisioned
        if (user.identityProviderUserId() != null) {
            auth0IdentityService.blockUser(user.identityProviderUserId());
        }
    }

    @Override
    @Transactional
    public void lockUser(LockUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        User.LockType lockType = User.LockType.valueOf(cmd.lockType());
        user.lock(lockType, cmd.actor());
        repository.save(user);

        // Block user in Auth0 if provisioned
        if (user.identityProviderUserId() != null) {
            auth0IdentityService.blockUser(user.identityProviderUserId());
        }
    }

    @Override
    @Transactional
    public void unlockUser(UnlockUserCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        User.LockType requesterLevel = User.LockType.valueOf(cmd.requesterLevel());
        user.unlock(requesterLevel, cmd.actor());
        repository.save(user);

        // Unblock user in Auth0 if provisioned
        if (user.identityProviderUserId() != null) {
            auth0IdentityService.unblockUser(user.identityProviderUserId());
        }
    }

    // ==================== Role Management ====================

    @Override
    @Transactional
    public void addRole(AddRoleCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        User.Role role = User.Role.valueOf(cmd.role());
        user.addRole(role);
        repository.save(user);
    }

    @Override
    @Transactional
    public void removeRole(RemoveRoleCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        User.Role role = User.Role.valueOf(cmd.role());
        user.removeRole(role);
        repository.save(user);
    }

    // ==================== User Updates ====================

    @Override
    @Transactional
    public void updateUserName(UpdateUserNameCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        user.updateName(cmd.firstName(), cmd.lastName());
        repository.save(user);
    }

    @Override
    @Transactional
    public UpdateEmailResult updateUserEmail(UpdateUserEmailCmd cmd) {
        User user = repository.findById(cmd.userId())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + cmd.userId().id()));

        // Check if new email is already in use by another user
        if (repository.existsByEmail(cmd.newEmail())) {
            User existingUser = repository.findByEmail(cmd.newEmail())
                .orElse(null);
            if (existingUser != null && !existingUser.id().equals(user.id())) {
                throw new IllegalArgumentException("Email already in use by another user");
            }
        }

        // Update email - this sets emailVerified to false
        String previousEmail = user.updateEmail(cmd.newEmail());
        repository.save(user);

        // Note: Do NOT update email in Auth0 - Auth0 email field contains loginId (not real email)
        // Real email is only stored locally and used for OTP delivery

        // Publish audit event
        eventPublisher.publishEvent(new UserEmailChanged(
            user.id().id(),
            previousEmail,
            cmd.newEmail(),
            cmd.updatedBy(),
            Instant.now()
        ));

        return new UpdateEmailResult(previousEmail, cmd.newEmail());
    }

    // ==================== Queries ====================

    @Override
    @Transactional(readOnly = true)
    public List<ProfileUserSummary> listUsersByProfile(ProfileId profileId) {
        return repository.findByProfileId(profileId).stream()
            .map(this::toProfileUserSummary)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Integer> countUsersByStatusForProfile(ProfileId profileId) {
        List<User> users = repository.findByProfileId(profileId);
        Map<String, Integer> counts = new HashMap<>();
        for (User user : users) {
            String status = user.status().name();
            counts.merge(status, 1, Integer::sum);
        }
        return counts;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetail getUserDetail(UserId userId) {
        User user = repository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId.id()));
        return toUserDetail(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummary getUserSummary(UserId userId) {
        User user = repository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId.id()));

        return new UserSummary(
            user.id().id(),
            user.email(),
            user.status().name(),
            user.userType().name(),
            user.identityProvider().name()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetail findByIdentityProviderUserId(String identityProviderUserId) {
        User user = repository.findByIdentityProviderUserId(identityProviderUserId)
            .orElseThrow(() -> new IllegalArgumentException("User not found for IdP user ID: " + identityProviderUserId));
        return toUserDetail(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetail findByEmail(String email) {
        User user = repository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found for email: " + email));
        return toUserDetail(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return repository.existsByEmail(email);
    }

    // ==================== Helper Methods ====================

    private ProfileUserSummary toProfileUserSummary(User user) {
        Set<String> roles = user.roles().stream()
            .map(Enum::name)
            .collect(Collectors.toSet());

        return new ProfileUserSummary(
            user.id().id(),
            user.loginId(),
            user.email(),
            user.firstName(),
            user.lastName(),
            user.status().name(),
            getStatusDisplayName(user.status()),
            user.lockType().name(),
            roles,
            user.createdAt(),
            user.lastLoggedInAt()
        );
    }

    private UserDetail toUserDetail(User user) {
        Set<String> roles = user.roles().stream()
            .map(Enum::name)
            .collect(Collectors.toSet());

        return new UserDetail(
            user.id().id(),
            user.loginId(),
            user.email(),
            user.firstName(),
            user.lastName(),
            user.status().name(),
            user.userType().name(),
            user.identityProvider().name(),
            user.profileId().urn(),
            user.identityProviderUserId(),
            roles,
            user.passwordSet(),
            user.mfaEnrolled(),
            user.createdAt(),
            user.createdBy(),
            user.lastSyncedAt(),
            user.lastLoggedInAt(),
            user.lockType().name(),
            user.lockedBy(),
            user.lockedAt(),
            user.deactivationReason()
        );
    }

    private String getStatusDisplayName(User.Status status) {
        return switch (status) {
            case PENDING_CREATION -> "Pending Creation";
            case PENDING_VERIFICATION -> "Pending Password";
            case PENDING_MFA -> "Pending MFA";
            case ACTIVE -> "Active";
            case LOCKED -> "Locked";
            case DEACTIVATED -> "Deactivated";
        };
    }
}
