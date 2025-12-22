package com.knight.application.rest.users.dto;

/**
 * Request to deactivate a user.
 */
public record DeactivateUserRequest(
    String reason
) {}
