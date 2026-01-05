package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

public record PasskeyListRequest(
    @NotBlank(message = "Login ID is required")
    String loginId
) {}
