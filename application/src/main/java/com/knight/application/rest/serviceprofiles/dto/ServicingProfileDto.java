package com.knight.application.rest.serviceprofiles.dto;

import java.time.Instant;

/**
 * DTO for Profile REST API responses.
 * Represents a client's servicing profile with service and account enrollments.
 */
public record ServicingProfileDto(
    String profileId,
    String clientId,
    String status,
    Instant createdAt
) {
}
