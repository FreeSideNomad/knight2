package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to send OTP for email verification.
 */
public record FtrSendOtpRequest(
    @NotBlank(message = "Login ID is required")
    String loginId
) {}
