package com.knight.domain.users.api.queries;

import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.UserId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Query interface for User Management.
 */
public interface UserQueries {

    // ==================== Profile Users ====================

    /**
     * List all users for a profile.
     */
    List<ProfileUserSummary> listUsersByProfile(ProfileId profileId);

    record ProfileUserSummary(
        String userId,
        String email,
        String firstName,
        String lastName,
        String status,
        String statusDisplayName,
        Set<String> roles,
        Instant createdAt,
        Instant lastLogin
    ) {}

    /**
     * Count users by status for a profile.
     */
    Map<String, Integer> countUsersByStatusForProfile(ProfileId profileId);

    // ==================== User Details ====================

    /**
     * Get detailed user information.
     */
    UserDetail getUserDetail(UserId userId);

    record UserDetail(
        String userId,
        String email,
        String firstName,
        String lastName,
        String status,
        String userType,
        String identityProvider,
        String profileId,
        String identityProviderUserId,
        Set<String> roles,
        boolean passwordSet,
        boolean mfaEnrolled,
        Instant createdAt,
        String createdBy,
        Instant lastSyncedAt,
        String lockReason,
        String deactivationReason
    ) {}

    // ==================== User Summary (legacy) ====================

    /**
     * Get user summary by ID.
     */
    UserSummary getUserSummary(UserId userId);

    record UserSummary(
        String userId,
        String email,
        String status,
        String userType,
        String identityProvider
    ) {}

    // ==================== Search ====================

    /**
     * Find user by identity provider user ID.
     */
    UserDetail findByIdentityProviderUserId(String identityProviderUserId);

    /**
     * Find user by email.
     */
    UserDetail findByEmail(String email);

    /**
     * Check if email exists.
     */
    boolean existsByEmail(String email);
}
