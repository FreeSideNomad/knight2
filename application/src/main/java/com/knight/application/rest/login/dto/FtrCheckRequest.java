package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to check FTR status for a user.
 */
public record FtrCheckRequest(
    @NotBlank(message = "Login ID is required")
    String loginId
) {}
