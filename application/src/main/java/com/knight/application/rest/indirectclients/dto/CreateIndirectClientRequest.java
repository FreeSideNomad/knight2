package com.knight.application.rest.indirectclients.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * REST DTO for creating a new indirect client.
 */
public record CreateIndirectClientRequest(
    @NotBlank(message = "Parent client ID is required")
    String parentClientId,

    @NotBlank(message = "Profile ID is required")
    String profileId,

    @NotBlank(message = "Business name is required")
    String businessName,

    @Valid
    List<RelatedPersonRequest> relatedPersons
) {}
