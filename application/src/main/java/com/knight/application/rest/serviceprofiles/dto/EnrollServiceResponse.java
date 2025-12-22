package com.knight.application.rest.serviceprofiles.dto;

import java.time.Instant;

/**
 * Response DTO for service enrollment.
 */
public record EnrollServiceResponse(
    String enrollmentId,
    String serviceType,
    String status,
    Instant enrolledAt,
    int linkedAccountCount
) {}
