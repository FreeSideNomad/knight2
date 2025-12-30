package com.knight.application.rest.login.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginCommand(
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @JsonAlias("email")
    String username,

    @NotBlank(message = "Password is required")
    String password
) {}
