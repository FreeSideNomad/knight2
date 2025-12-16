package com.knight.application.rest.serviceprofiles.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for detailed profile information.
 */
public record ProfileDetailDto(
    String profileId,
    String name,
    String profileType,
    String status,
    String createdBy,
    Instant createdAt,
    Instant updatedAt,
    List<ClientEnrollmentDto> clientEnrollments,
    List<ServiceEnrollmentDto> serviceEnrollments,
    List<AccountEnrollmentDto> accountEnrollments
) {
    public record ClientEnrollmentDto(
        String clientId,
        String clientName,
        boolean isPrimary,
        String accountEnrollmentType,
        Instant enrolledAt
    ) {}

    public record ServiceEnrollmentDto(
        String enrollmentId,
        String serviceType,
        String status,
        String configuration,
        Instant enrolledAt
    ) {}

    public record AccountEnrollmentDto(
        String enrollmentId,
        String clientId,
        String accountId,
        String serviceEnrollmentId,
        String status,
        Instant enrolledAt
    ) {}
}
