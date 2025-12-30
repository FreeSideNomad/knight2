package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record TokenExchangeRequest(
    @NotBlank(message = "Authorization code is required")
    String code,

    @NotBlank(message = "Redirect URI is required")
    @JsonProperty("redirect_uri")
    String redirectUri
) {}
