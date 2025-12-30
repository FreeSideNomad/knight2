package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record AssociateMfaCommand(
    @NotBlank(message = "MFA token is required")
    @JsonProperty("mfa_token")
    String mfaToken,

    @JsonProperty("authenticator_type")
    String authenticatorType
) {
    public AssociateMfaCommand {
        if (authenticatorType == null || authenticatorType.isBlank()) {
            authenticatorType = "otp";
        }
    }
}
