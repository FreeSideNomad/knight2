package com.knight.application.rest.clients.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * REST DTO for detailed client information.
 * Contains complete client data including address and timestamps.
 */
public record ClientDetailDto(
    @NotBlank(message = "Client ID is required")
    String clientId,

    @NotBlank(message = "Client name is required")
    String name,

    @NotBlank(message = "Client type is required")
    String clientType,

    @NotNull(message = "Address is required")
    @Valid
    AddressDto address,

    @NotNull(message = "Creation timestamp is required")
    Instant createdAt,

    @NotNull(message = "Update timestamp is required")
    Instant updatedAt
) {
}
