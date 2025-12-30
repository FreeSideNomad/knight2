package com.knight.application.rest.client;

import com.knight.application.rest.indirectclients.dto.RelatedPersonRequest;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request to create an indirect client for the current user's profile.
 * Note: parentProfileId is derived from JWT, not passed in request.
 */
public record CreateIndirectClientForProfileRequest(
    @NotBlank(message = "Parent client ID is required")
    String parentClientId,

    @NotBlank(message = "Name is required")
    String name,

    List<RelatedPersonRequest> relatedPersons
) {}
