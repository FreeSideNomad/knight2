package com.knight.application.rest.indirectclients.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * REST DTO for adding a related person to an indirect client.
 */
public record RelatedPersonRequest(
    @NotBlank(message = "Name is required")
    String name,

    @NotBlank(message = "Role is required")
    String role,

    String email,
    String phone
) {}
