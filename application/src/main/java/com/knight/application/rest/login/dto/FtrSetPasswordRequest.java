package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to set user password during FTR.
 */
public record FtrSetPasswordRequest(
    @NotBlank(message = "Login ID is required")
    @JsonProperty("login_id")
    String loginId,

    @NotBlank(message = "Password is required")
    @Size(min = 8, message = "Password must be at least 8 characters")
    String password
) {}
