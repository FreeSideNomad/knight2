package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Verify OTP for password reset flow.
 */
public record PasswordResetVerifyOtpCommand(
    @NotBlank(message = "Login ID is required")
    String loginId,

    @NotBlank(message = "OTP code is required")
    @Size(min = 6, max = 6, message = "OTP must be 6 digits")
    String code
) {}
