package com.knight.application.events;

import com.knight.platform.sharedkernel.UserId;

/**
 * Domain event published when a user sets their password.
 */
public record UserPasswordSetEvent(
    UserId userId,
    String auth0UserId
) {}
