package com.knight.application.rest.client;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to update a business name.
 */
public record UpdateNameRequest(
    @NotBlank(message = "Name is required")
    String name
) {}
