package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to initiate password reset flow.
 * Sends OTP to user's registered email.
 */
public record PasswordResetRequestCommand(
    @NotBlank(message = "Login ID is required")
    String loginId
) {}
