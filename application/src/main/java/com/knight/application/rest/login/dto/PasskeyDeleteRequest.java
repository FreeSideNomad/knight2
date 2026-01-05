package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

public record PasskeyDeleteRequest(
    @NotBlank(message = "Login ID is required")
    String loginId,

    @NotBlank(message = "Passkey ID is required")
    String passkeyId
) {}
