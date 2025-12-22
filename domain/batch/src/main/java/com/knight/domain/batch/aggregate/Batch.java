package com.knight.domain.batch.aggregate;

import com.knight.domain.batch.types.BatchItemStatus;
import com.knight.domain.batch.types.BatchStatus;
import com.knight.domain.batch.types.BatchType;
import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.BatchItemId;
import com.knight.platform.sharedkernel.ProfileId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Batch aggregate root.
 * Manages batch processing operations with individual items.
 */
public class Batch {

    /**
     * Individual item within a batch.
     */
    public static class BatchItem {
        private final BatchItemId id;
        private final int sequenceNumber;
        private final String inputData;
        private BatchItemStatus status;
        private String resultData;
        private String errorMessage;
        private Instant processedAt;

        public BatchItem(int sequenceNumber, String inputData) {
            this.id = BatchItemId.generate();
            this.sequenceNumber = sequenceNumber;
            this.inputData = Objects.requireNonNull(inputData, "inputData cannot be null");
            this.status = BatchItemStatus.PENDING;
        }

        // Constructor for reconstruction from persistence
        public BatchItem(BatchItemId id, int sequenceNumber, String inputData,
                         BatchItemStatus status, String resultData, String errorMessage,
                         Instant processedAt) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.sequenceNumber = sequenceNumber;
            this.inputData = inputData;
            this.status = status;
            this.resultData = resultData;
            this.errorMessage = errorMessage;
            this.processedAt = processedAt;
        }

        public void markInProgress() {
            if (this.status != BatchItemStatus.PENDING) {
                throw new IllegalStateException("Can only start processing PENDING items");
            }
            this.status = BatchItemStatus.IN_PROGRESS;
        }

        public void markSuccess(String resultData) {
            if (this.status != BatchItemStatus.IN_PROGRESS) {
                throw new IllegalStateException("Can only mark IN_PROGRESS items as success");
            }
            this.status = BatchItemStatus.SUCCESS;
            this.resultData = resultData;
            this.processedAt = Instant.now();
        }

        public void markFailed(String errorMessage) {
            if (this.status != BatchItemStatus.IN_PROGRESS) {
                throw new IllegalStateException("Can only mark IN_PROGRESS items as failed");
            }
            this.status = BatchItemStatus.FAILED;
            this.errorMessage = errorMessage;
            this.processedAt = Instant.now();
        }

        // Getters
        public BatchItemId id() { return id; }
        public int sequenceNumber() { return sequenceNumber; }
        public String inputData() { return inputData; }
        public BatchItemStatus status() { return status; }
        public String resultData() { return resultData; }
        public String errorMessage() { return errorMessage; }
        public Instant processedAt() { return processedAt; }
    }

    private final BatchId id;
    private final BatchType type;
    private final ProfileId sourceProfileId;
    private BatchStatus status;
    private int totalItems;
    private int successCount;
    private int failedCount;
    private final List<BatchItem> items;
    private final Instant createdAt;
    private final String createdBy;
    private Instant startedAt;
    private Instant completedAt;

    private Batch(BatchId id, BatchType type, ProfileId sourceProfileId, String createdBy) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.sourceProfileId = Objects.requireNonNull(sourceProfileId, "sourceProfileId cannot be null");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy cannot be null");
        this.status = BatchStatus.PENDING;
        this.totalItems = 0;
        this.successCount = 0;
        this.failedCount = 0;
        this.items = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    /**
     * Create a new batch.
     */
    public static Batch create(BatchType type, ProfileId sourceProfileId, String createdBy) {
        return new Batch(BatchId.generate(), type, sourceProfileId, createdBy);
    }

    /**
     * Factory method for reconstitution from persistence.
     */
    public static Batch reconstitute(
            BatchId id, BatchType type, ProfileId sourceProfileId,
            BatchStatus status, int totalItems, int successCount, int failedCount,
            List<BatchItem> items, Instant createdAt, String createdBy,
            Instant startedAt, Instant completedAt) {

        Batch batch = new Batch(id, type, sourceProfileId, createdBy);
        batch.status = status;
        batch.totalItems = totalItems;
        batch.successCount = successCount;
        batch.failedCount = failedCount;
        batch.items.addAll(items);
        batch.startedAt = startedAt;
        batch.completedAt = completedAt;

        // Override createdAt from persistence
        try {
            var createdAtField = Batch.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(batch, createdAt);
        } catch (Exception e) {
            // Ignore - use default
        }

        return batch;
    }

    /**
     * Add an item to the batch.
     */
    public BatchItem addItem(String inputData) {
        if (this.status != BatchStatus.PENDING) {
            throw new IllegalStateException("Cannot add items to batch in status: " + this.status);
        }
        int sequenceNumber = this.items.size() + 1;
        BatchItem item = new BatchItem(sequenceNumber, inputData);
        this.items.add(item);
        this.totalItems = this.items.size();
        return item;
    }

    /**
     * Start processing the batch.
     */
    public void start() {
        if (this.status != BatchStatus.PENDING) {
            throw new IllegalStateException("Can only start PENDING batches");
        }
        if (this.items.isEmpty()) {
            throw new IllegalStateException("Cannot start batch with no items");
        }
        this.status = BatchStatus.IN_PROGRESS;
        this.startedAt = Instant.now();
    }

    /**
     * Increment success count after an item succeeds.
     */
    public void incrementSuccess() {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only update counts for IN_PROGRESS batches");
        }
        this.successCount++;
    }

    /**
     * Increment failed count after an item fails.
     */
    public void incrementFailed() {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only update counts for IN_PROGRESS batches");
        }
        this.failedCount++;
    }

    /**
     * Complete the batch processing.
     */
    public void complete() {
        if (this.status != BatchStatus.IN_PROGRESS) {
            throw new IllegalStateException("Can only complete IN_PROGRESS batches");
        }
        this.completedAt = Instant.now();
        if (this.failedCount == 0) {
            this.status = BatchStatus.COMPLETED;
        } else if (this.successCount > 0) {
            this.status = BatchStatus.COMPLETED_WITH_ERRORS;
        } else {
            this.status = BatchStatus.FAILED;
        }
    }

    /**
     * Mark the batch as failed due to a batch-level error.
     */
    public void fail(String reason) {
        this.status = BatchStatus.FAILED;
        this.completedAt = Instant.now();
    }

    /**
     * Get the next pending item to process.
     */
    public BatchItem getNextPendingItem() {
        return this.items.stream()
                .filter(item -> item.status() == BatchItemStatus.PENDING)
                .findFirst()
                .orElse(null);
    }

    /**
     * Get pending items count.
     */
    public int pendingCount() {
        return this.totalItems - this.successCount - this.failedCount;
    }

    // Getters
    public BatchId id() { return id; }
    public BatchType type() { return type; }
    public ProfileId sourceProfileId() { return sourceProfileId; }
    public BatchStatus status() { return status; }
    public int totalItems() { return totalItems; }
    public int successCount() { return successCount; }
    public int failedCount() { return failedCount; }
    public List<BatchItem> items() { return List.copyOf(items); }
    public Instant createdAt() { return createdAt; }
    public String createdBy() { return createdBy; }
    public Instant startedAt() { return startedAt; }
    public Instant completedAt() { return completedAt; }
}
