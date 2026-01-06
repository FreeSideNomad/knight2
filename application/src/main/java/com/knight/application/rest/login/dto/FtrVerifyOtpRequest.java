package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to verify OTP code.
 */
public record FtrVerifyOtpRequest(
    @NotBlank(message = "Login ID is required")
    @JsonProperty("login_id")
    String loginId,

    @NotBlank(message = "Verification code is required")
    @Size(min = 6, max = 6, message = "Verification code must be 6 digits")
    String code
) {}
