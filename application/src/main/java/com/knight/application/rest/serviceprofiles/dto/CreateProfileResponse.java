package com.knight.application.rest.serviceprofiles.dto;

/**
 * Response DTO for profile creation.
 */
public record CreateProfileResponse(
    String profileId,
    String name
) {}
