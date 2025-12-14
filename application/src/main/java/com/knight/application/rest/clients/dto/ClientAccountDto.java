package com.knight.application.rest.clients.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * REST DTO for client account information.
 * Contains account details including currency and status.
 */
public record ClientAccountDto(
    @NotBlank(message = "Account ID is required")
    String accountId,

    @NotBlank(message = "Client ID is required")
    String clientId,

    @NotBlank(message = "Currency is required")
    String currency,

    @NotBlank(message = "Status is required")
    String status,

    @NotNull(message = "Creation timestamp is required")
    Instant createdAt
) {
}
