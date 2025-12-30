package com.knight.application.rest.users.dto;

import java.time.Instant;
import java.util.Set;

/**
 * User list item for profile page.
 */
public record ProfileUserDto(
    String userId,
    String loginId,
    String email,
    String firstName,
    String lastName,
    String status,
    String statusDisplayName,
    Set<String> roles,
    boolean canResendInvitation,
    boolean canLock,
    boolean canDeactivate,
    Instant createdAt,
    Instant lastLoggedInAt
) {}
