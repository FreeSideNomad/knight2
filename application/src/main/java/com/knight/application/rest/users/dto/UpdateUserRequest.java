package com.knight.application.rest.users.dto;

/**
 * Request to update user details.
 */
public record UpdateUserRequest(
    String firstName,
    String lastName
) {}
