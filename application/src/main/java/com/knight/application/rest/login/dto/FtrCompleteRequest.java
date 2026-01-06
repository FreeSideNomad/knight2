package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to complete FTR flow.
 */
public record FtrCompleteRequest(
    @NotBlank(message = "Login ID is required")
    @JsonProperty("login_id")
    String loginId,

    @JsonProperty("mfa_enrolled")
    Boolean mfaEnrolled
) {}
