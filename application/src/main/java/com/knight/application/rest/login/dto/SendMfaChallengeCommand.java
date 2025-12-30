package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record SendMfaChallengeCommand(
    @NotBlank(message = "MFA token is required")
    @JsonProperty("mfa_token")
    String mfaToken,

    @JsonProperty("authenticator_id")
    String authenticatorId,

    @JsonProperty("authenticator_type")
    String authenticatorType
) {
    public SendMfaChallengeCommand {
        if (authenticatorType == null || authenticatorType.isBlank()) {
            authenticatorType = "oob";
        }
    }
}
