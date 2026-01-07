package com.knight.domain.auth0identity.api;

import com.knight.platform.sharedkernel.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Auth0 Identity Service interface.
 * Defines the contract for Auth0 identity operations.
 * Acts as an anti-corruption layer between the domain and Auth0.
 */
public interface Auth0IdentityService {

    // ==================== User Provisioning ====================

    /**
     * Provisions a new user in Auth0.
     * Creates user with temporary password and triggers password reset email.
     *
     * @param request the provisioning request
     * @return the provisioning result with Auth0 user ID and reset URL
     */
    ProvisionUserResult provisionUser(ProvisionUserRequest request);

    record ProvisionUserRequest(
        String loginId,      // Used as email field in Auth0 (must be valid email format)
        String email,        // Real email for OTP delivery (stored in local DB only)
        String firstName,
        String lastName,
        String internalUserId,
        String profileId
    ) {}

    record ProvisionUserResult(
        String identityProviderUserId,
        String passwordResetUrl,
        Instant provisionedAt
    ) {}

    // ==================== Onboarding Status ====================

    /**
     * Onboarding state enum - represents user's progress through onboarding.
     */
    enum OnboardingState {
        PENDING_PASSWORD,   // User created, awaiting password set
        PENDING_MFA,        // Password set, awaiting MFA enrollment
        COMPLETE            // Fully onboarded
    }

    /**
     * Gets the current onboarding status from Auth0.
     * Checks password set and MFA enrollment status.
     */
    OnboardingStatus getOnboardingStatus(String identityProviderUserId);

    record OnboardingStatus(
        String identityProviderUserId,
        boolean passwordSet,
        boolean mfaEnrolled,
        OnboardingState state,
        Instant lastLogin
    ) {}

    /**
     * Resends the password reset email.
     *
     * @param identityProviderUserId the Auth0 user ID
     * @return the new password reset URL
     */
    String resendPasswordResetEmail(String identityProviderUserId);

    // ==================== Basic CRUD Operations ====================

    /**
     * Creates a new user in Auth0.
     *
     * @param request the user creation request
     * @return the Auth0 user ID
     */
    String createUser(CreateAuth0UserRequest request);

    /**
     * Retrieves user information from Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     * @return the user info if found
     */
    Optional<Auth0UserInfo> getUser(String auth0UserId);

    /**
     * Retrieves user by email from Auth0.
     *
     * @param email the email address
     * @return the user info if found
     */
    Optional<Auth0UserInfo> getUserByEmail(String email);

    /**
     * Updates a user in Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     * @param request the update request
     */
    void updateUser(String auth0UserId, UpdateAuth0UserRequest request);

    /**
     * Updates a user's email address in Auth0.
     * This is a convenience method that calls updateUser with just the email field.
     * Note: This does NOT set email_verified in Auth0 - we manage email verification ourselves.
     *
     * @param auth0UserId the Auth0 user ID
     * @param newEmail the new email address
     */
    default void updateUserEmail(String auth0UserId, String newEmail) {
        updateUser(auth0UserId, new UpdateAuth0UserRequest(null, newEmail, null));
    }

    /**
     * Blocks a user in Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     */
    void blockUser(String auth0UserId);

    /**
     * Unblocks a user in Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     */
    void unblockUser(String auth0UserId);

    /**
     * Deletes a user from Auth0.
     *
     * @param auth0UserId the Auth0 user ID
     */
    void deleteUser(String auth0UserId);

    /**
     * Sends a password reset email to the user.
     *
     * @param email the user's email address
     */
    void sendPasswordResetEmail(String email);

    // ==================== MFA Management ====================

    /**
     * Deletes all MFA enrollments for a user.
     * Called when admin resets MFA for a user.
     *
     * @param auth0UserId the Auth0 user ID
     */
    void deleteAllMfaEnrollments(String auth0UserId);

    /**
     * Links the Auth0 user to an internal user ID.
     *
     * @param auth0UserId the Auth0 user ID
     * @param internalUserId the internal user ID
     */
    void linkToInternalUser(String auth0UserId, UserId internalUserId);

    // ==================== Request/Response Records ====================

    record CreateAuth0UserRequest(
        String email,
        String name,
        String connection,
        boolean emailVerified,
        String password
    ) {
        public CreateAuth0UserRequest(String email, String name) {
            this(email, name, "Username-Password-Authentication", false, null);
        }
    }

    record UpdateAuth0UserRequest(
        String name,
        String email,
        Boolean blocked
    ) {}

    record Auth0UserInfo(
        String auth0UserId,
        String email,
        String name,
        boolean emailVerified,
        boolean blocked,
        String picture,
        String lastLogin
    ) {}
}
