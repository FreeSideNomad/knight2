package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank(message = "Refresh token is required")
    @JsonProperty("refresh_token")
    String refreshToken
) {}
