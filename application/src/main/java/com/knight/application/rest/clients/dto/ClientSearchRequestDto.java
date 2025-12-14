package com.knight.application.rest.clients.dto;

/**
 * REST DTO for client search request parameters.
 * Both fields are optional - if none provided, all clients are returned.
 */
public record ClientSearchRequestDto(
    String type,    // Optional: "srf" or "cdr"
    String name     // Optional: name pattern to search for
) {
}
