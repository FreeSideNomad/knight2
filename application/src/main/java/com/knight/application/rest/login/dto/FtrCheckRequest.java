package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * Request to check FTR status for a user.
 */
public record FtrCheckRequest(
    @NotBlank(message = "Login ID is required")
    @JsonProperty("login_id")
    String loginId
) {}
