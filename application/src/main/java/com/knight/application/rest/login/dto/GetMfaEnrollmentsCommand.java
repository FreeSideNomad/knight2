package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record GetMfaEnrollmentsCommand(
    @NotBlank(message = "MFA token is required")
    @JsonProperty("mfa_token")
    String mfaToken
) {}
