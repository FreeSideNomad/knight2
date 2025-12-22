package com.knight.application.rest.batch.dto;

import java.time.Instant;

/**
 * DTO for batch detail response.
 */
public record BatchDetailDto(
        String batchId,
        String batchType,
        String batchTypeDisplayName,
        String sourceProfileId,
        String status,
        String statusDisplayName,
        int totalItems,
        int successCount,
        int failedCount,
        int pendingCount,
        Instant createdAt,
        String createdBy,
        Instant startedAt,
        Instant completedAt
) {}
