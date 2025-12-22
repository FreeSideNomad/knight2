package com.knight.application.events;

import com.knight.platform.sharedkernel.UserId;

/**
 * Domain event published when a user completes onboarding.
 */
public record UserOnboardingCompletedEvent(
    UserId userId,
    String auth0UserId
) {}
