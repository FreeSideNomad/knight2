package com.knight.domain.batch.api.queries;

import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.List;

/**
 * Queries for batch operations.
 */
public interface BatchQueries {

    /**
     * Get batch details by ID.
     */
    BatchDetail getBatch(BatchId batchId);

    /**
     * List batches for a profile.
     */
    List<BatchSummary> listBatchesByProfile(ProfileId profileId);

    /**
     * Get items for a batch, optionally filtered by status.
     */
    List<BatchItemDetail> getBatchItems(BatchId batchId, String status);

    // Response records

    record BatchDetail(
            String batchId,
            String batchType,
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

    record BatchSummary(
            String batchId,
            String batchType,
            String status,
            String statusDisplayName,
            int totalItems,
            int successCount,
            int failedCount,
            Instant createdAt
    ) {}

    record BatchItemDetail(
            String batchItemId,
            int sequenceNumber,
            String inputData,
            String status,
            String statusDisplayName,
            String resultData,
            String errorMessage,
            Instant processedAt
    ) {}
}
