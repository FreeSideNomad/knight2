package com.knight.application.rest.users.dto;

import java.util.Set;

/**
 * Request to add a user to a profile.
 */
public record AddUserRequest(
    String loginId,
    String email,
    String firstName,
    String lastName,
    Set<String> roles
) {}
