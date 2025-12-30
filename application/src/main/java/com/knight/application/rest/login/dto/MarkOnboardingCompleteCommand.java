package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record MarkOnboardingCompleteCommand(
    @NotBlank(message = "User ID is required")
    @JsonProperty("user_id")
    String userId
) {}
