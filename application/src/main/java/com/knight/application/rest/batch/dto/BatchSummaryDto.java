package com.knight.application.rest.batch.dto;

import java.time.Instant;

/**
 * DTO for batch summary in lists.
 */
public record BatchSummaryDto(
        String batchId,
        String batchType,
        String status,
        String statusDisplayName,
        int totalItems,
        int successCount,
        int failedCount,
        Instant createdAt
) {}
