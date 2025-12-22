package com.knight.application.rest.users.dto;

/**
 * Request to lock a user.
 */
public record LockUserRequest(
    String reason
) {}
