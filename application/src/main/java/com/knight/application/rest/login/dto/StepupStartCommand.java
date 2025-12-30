package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record StepupStartCommand(
    @NotBlank(message = "MFA token is required")
    @JsonProperty("mfa_token")
    String mfaToken,

    String message
) {
    public StepupStartCommand {
        if (message == null || message.isBlank()) {
            message = "Approve this action";
        }
    }
}
