package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record VerifyMfaCommand(
    @NotBlank(message = "MFA token is required")
    @JsonProperty("mfa_token")
    String mfaToken,

    @NotBlank(message = "OTP is required")
    String otp,

    @JsonProperty("authenticator_type")
    String authenticatorType,

    String email
) {
    public VerifyMfaCommand {
        if (authenticatorType == null || authenticatorType.isBlank()) {
            authenticatorType = "otp";
        }
    }
}
