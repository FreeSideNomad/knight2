package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record VerifyMfaChallengeCommand(
    @NotBlank(message = "MFA token is required")
    @JsonProperty("mfa_token")
    String mfaToken,

    String otp,

    @JsonProperty("oob_code")
    String oobCode,

    @JsonProperty("authenticator_type")
    String authenticatorType,

    String email
) {
    public VerifyMfaChallengeCommand {
        if (authenticatorType == null || authenticatorType.isBlank()) {
            authenticatorType = "otp";
        }
    }
}
