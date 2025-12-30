package com.knight.domain.users.api.events;

import java.time.Instant;
import java.util.Set;

/**
 * Domain event published when a user is created.
 */
public record UserCreated(
    String userId,
    String loginId,
    String email,
    String firstName,
    String lastName,
    String userType,
    String identityProvider,
    String profileId,
    Set<String> roles,
    Instant createdAt
) {}
