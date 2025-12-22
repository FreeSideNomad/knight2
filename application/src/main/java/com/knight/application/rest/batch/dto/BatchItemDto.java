package com.knight.application.rest.batch.dto;

import java.time.Instant;
import java.util.List;

/**
 * DTO for individual batch item.
 */
public record BatchItemDto(
        String batchItemId,
        int sequenceNumber,
        String businessName,
        String status,
        String statusDisplayName,
        BatchItemResultDto result,
        String errorMessage,
        Instant processedAt
) {
    public record BatchItemResultDto(
            String indirectClientId,
            String profileId,
            List<String> userIds
    ) {}
}
