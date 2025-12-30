package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;

public record StepupRefreshTokenCommand(
    @NotBlank(message = "Email is required")
    @JsonAlias("username")
    String email,

    @NotBlank(message = "Password is required")
    String password
) {}
