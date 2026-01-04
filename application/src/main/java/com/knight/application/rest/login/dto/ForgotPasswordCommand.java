package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordCommand(
    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @JsonProperty("email")
    String email
) {}
