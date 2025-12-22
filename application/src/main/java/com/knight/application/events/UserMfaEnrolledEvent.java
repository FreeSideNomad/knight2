package com.knight.application.events;

import com.knight.platform.sharedkernel.UserId;

/**
 * Domain event published when a user enrolls in MFA.
 */
public record UserMfaEnrolledEvent(
    UserId userId,
    String auth0UserId
) {}
