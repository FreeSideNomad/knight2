package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChallengeMfaCommand(
    @NotBlank(message = "MFA token is required")
    @JsonProperty("mfa_token")
    String mfaToken,

    @NotBlank(message = "OOB code is required")
    @JsonProperty("oob_code")
    String oobCode,

    String email
) {}
