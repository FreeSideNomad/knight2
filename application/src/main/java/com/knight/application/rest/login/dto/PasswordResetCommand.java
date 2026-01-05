package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Set new password after OTP verification.
 */
public record PasswordResetCommand(
    @NotBlank(message = "Login ID is required")
    String loginId,

    @NotBlank(message = "Reset token is required")
    String resetToken,

    @NotBlank(message = "Password is required")
    @Size(min = 12, message = "Password must be at least 12 characters")
    String password
) {}
