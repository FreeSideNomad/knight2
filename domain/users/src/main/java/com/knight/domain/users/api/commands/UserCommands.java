package com.knight.domain.users.api.commands;

import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.util.Set;

/**
 * Command interface for User Management.
 * Defines contract for creating and managing users.
 */
public interface UserCommands {

    // ==================== User Creation ====================

    /**
     * Creates a new user (local only, not provisioned to IdP).
     */
    UserId createUser(CreateUserCmd cmd);

    record CreateUserCmd(
        String loginId,
        String email,
        String firstName,
        String lastName,
        String userType,
        String identityProvider,
        ProfileId profileId,
        Set<String> roles,
        String createdBy
    ) {}

    // ==================== Identity Provider Provisioning ====================

    /**
     * Provisions user to identity provider (Auth0).
     * Returns provisioning result with IdP user ID and password reset URL.
     */
    ProvisionResult provisionUser(ProvisionUserCmd cmd);

    record ProvisionUserCmd(UserId userId) {}

    record ProvisionResult(
        String identityProviderUserId,
        String passwordResetUrl
    ) {}

    /**
     * Updates onboarding status from identity provider events.
     */
    void updateOnboardingStatus(UpdateOnboardingStatusCmd cmd);

    record UpdateOnboardingStatusCmd(
        String identityProviderUserId,
        boolean emailVerified,
        boolean passwordSet,
        boolean mfaEnrolled
    ) {}

    /**
     * Resends invitation (password reset email) to user.
     * Returns the new password reset URL.
     */
    String resendInvitation(ResendInvitationCmd cmd);

    record ResendInvitationCmd(UserId userId) {}

    // ==================== User Lifecycle ====================

    void activateUser(ActivateUserCmd cmd);

    record ActivateUserCmd(UserId userId) {}

    void deactivateUser(DeactivateUserCmd cmd);

    record DeactivateUserCmd(UserId userId, String reason) {}

    void lockUser(LockUserCmd cmd);

    record LockUserCmd(UserId userId, String lockType, String actor) {}

    void unlockUser(UnlockUserCmd cmd);

    record UnlockUserCmd(UserId userId, String requesterLevel, String actor) {}

    // ==================== Role Management ====================

    void addRole(AddRoleCmd cmd);

    record AddRoleCmd(UserId userId, String role) {}

    void removeRole(RemoveRoleCmd cmd);

    record RemoveRoleCmd(UserId userId, String role) {}

    // ==================== User Updates ====================

    void updateUserName(UpdateUserNameCmd cmd);

    record UpdateUserNameCmd(UserId userId, String firstName, String lastName) {}
}
