package com.knight.application.rest.indirectprofiles.dto;

import java.time.Instant;

/**
 * DTO representing an indirect profile summary.
 */
public record IndirectProfileSummaryDto(
    String profileId,
    String name,
    String profileType,
    String status,
    String primaryClientId,
    int clientEnrollmentCount,
    int serviceEnrollmentCount,
    int accountEnrollmentCount,
    Instant createdAt,
    String createdBy
) {}
