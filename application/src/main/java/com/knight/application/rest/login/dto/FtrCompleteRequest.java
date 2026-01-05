package com.knight.application.rest.login.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to complete FTR flow.
 */
public record FtrCompleteRequest(
    @NotBlank(message = "Login ID is required")
    String loginId,

    Boolean mfaEnrolled
) {}
