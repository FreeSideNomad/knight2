package com.knight.application.rest.indirectclients.dto;

import java.time.Instant;

/**
 * REST DTO for indirect client list view.
 * Contains summary information for displaying indirect clients.
 */
public record IndirectClientDto(
    String id,
    String parentClientId,
    String clientType,
    String businessName,
    String status,
    int relatedPersonCount,
    Instant createdAt
) {}
