package com.knight.application.rest.serviceprofiles.dto;

import java.time.Instant;

/**
 * DTO for OnlineProfile REST API responses.
 * Represents a client's online banking profile with site configuration.
 */
public record OnlineProfileDto(
    String profileId,
    String clientId,
    String siteId,
    String status,
    Instant createdAt
) {
}
