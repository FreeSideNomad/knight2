package com.knight.application.rest.serviceprofiles.dto;

/**
 * DTO for profile summary in REST API responses.
 * Provides a high-level overview of any profile type.
 */
public record ProfileSummaryDto(
    String profileId,
    String profileType,
    String status
) {
}
