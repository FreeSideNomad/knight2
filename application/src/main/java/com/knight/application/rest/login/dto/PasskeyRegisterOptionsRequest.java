package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

public record PasskeyRegisterOptionsRequest(
    @NotBlank(message = "Login ID is required")
    String loginId
) {}
