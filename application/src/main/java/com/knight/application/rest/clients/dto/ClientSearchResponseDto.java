package com.knight.application.rest.clients.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * REST DTO for client search results.
 * Contains minimal information needed for client selection.
 */
public record ClientSearchResponseDto(
    @NotBlank(message = "Client ID is required")
    String clientId,

    @NotBlank(message = "Client name is required")
    String name,

    @NotBlank(message = "Client type is required")
    String clientType
) {
}
