package com.knight.application.rest.serviceprofiles.dto;

import java.util.List;

/**
 * Request DTO for creating a profile with client and account enrollments.
 */
public record CreateProfileRequest(
    String profileType,
    String name,  // Optional - defaults to primary client name
    List<ClientAccountSelectionDto> clients
) {}
