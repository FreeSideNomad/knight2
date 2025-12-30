package com.knight.application.rest.users.dto;

import java.time.Instant;
import java.util.Set;

/**
 * Detailed user view.
 */
public record UserDetailDto(
    String userId,
    String loginId,
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
    Instant lastLoggedInAt,
    String lockType,
    String lockedBy,
    Instant lockedAt,
    String deactivationReason
) {}
