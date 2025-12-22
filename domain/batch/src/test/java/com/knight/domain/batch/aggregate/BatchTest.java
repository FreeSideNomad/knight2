package com.knight.domain.batch.aggregate;

import com.knight.domain.batch.types.BatchItemStatus;
import com.knight.domain.batch.types.BatchStatus;
import com.knight.domain.batch.types.BatchType;
import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.BatchItemId;
import com.knight.platform.sharedkernel.ProfileId;
import com.knight.platform.sharedkernel.SrfClientId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Batch Aggregate Tests")
class BatchTest {

    private static final ProfileId TEST_PROFILE_ID = ProfileId.of("servicing", new SrfClientId("123456789"));
    private static final String TEST_USER = "test-user@example.com";

    @Nested
    @DisplayName("Batch Creation")
    class CreateTests {

        @Test
        @DisplayName("create() should create batch with PENDING status")
        void createBatchWithPendingStatus() {
            // When
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);

            // Then
            assertThat(batch).isNotNull();
            assertThat(batch.id()).isNotNull();
            assertThat(batch.type()).isEqualTo(BatchType.PAYOR_ENROLMENT);
            assertThat(batch.sourceProfileId()).isEqualTo(TEST_PROFILE_ID);
            assertThat(batch.status()).isEqualTo(BatchStatus.PENDING);
            assertThat(batch.totalItems()).isZero();
            assertThat(batch.successCount()).isZero();
            assertThat(batch.failedCount()).isZero();
            assertThat(batch.items()).isEmpty();
            assertThat(batch.createdAt()).isNotNull();
            assertThat(batch.createdBy()).isEqualTo(TEST_USER);
            assertThat(batch.startedAt()).isNull();
            assertThat(batch.completedAt()).isNull();
        }

        @Test
        @DisplayName("create() should reject null type")
        void createBatchRejectsNullType() {
            assertThatThrownBy(() -> Batch.create(null, TEST_PROFILE_ID, TEST_USER))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("type cannot be null");
        }

        @Test
        @DisplayName("create() should reject null profile ID")
        void createBatchRejectsNullProfileId() {
            assertThatThrownBy(() -> Batch.create(BatchType.PAYOR_ENROLMENT, null, TEST_USER))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("sourceProfileId cannot be null");
        }

        @Test
        @DisplayName("create() should reject null created by")
        void createBatchRejectsNullCreatedBy() {
            assertThatThrownBy(() -> Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("createdBy cannot be null");
        }
    }

    @Nested
    @DisplayName("Adding Items")
    class AddItemTests {

        @Test
        @DisplayName("addItem() should add item with sequence number and PENDING status")
        void addItemSuccessfully() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);

            // When
            Batch.BatchItem item1 = batch.addItem("data-1");
            Batch.BatchItem item2 = batch.addItem("data-2");
            Batch.BatchItem item3 = batch.addItem("data-3");

            // Then
            assertThat(batch.totalItems()).isEqualTo(3);
            assertThat(batch.items()).hasSize(3);

            assertThat(item1.sequenceNumber()).isEqualTo(1);
            assertThat(item1.inputData()).isEqualTo("data-1");
            assertThat(item1.status()).isEqualTo(BatchItemStatus.PENDING);
            assertThat(item1.id()).isNotNull();

            assertThat(item2.sequenceNumber()).isEqualTo(2);
            assertThat(item2.inputData()).isEqualTo("data-2");

            assertThat(item3.sequenceNumber()).isEqualTo(3);
            assertThat(item3.inputData()).isEqualTo("data-3");
        }

        @Test
        @DisplayName("addItem() should reject null input data")
        void addItemRejectsNullInputData() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);

            // When/Then
            assertThatThrownBy(() -> batch.addItem(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("inputData cannot be null");
        }

        @Test
        @DisplayName("addItem() should fail when batch is IN_PROGRESS")
        void addItemFailsWhenInProgress() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.start();

            // When/Then
            assertThatThrownBy(() -> batch.addItem("data-2"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot add items to batch in status: IN_PROGRESS");
        }

        @Test
        @DisplayName("addItem() should fail when batch is COMPLETED")
        void addItemFailsWhenCompleted() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.start();
            batch.items().get(0).markInProgress();
            batch.items().get(0).markSuccess("result");
            batch.incrementSuccess();
            batch.complete();

            // When/Then
            assertThatThrownBy(() -> batch.addItem("data-2"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot add items to batch in status: COMPLETED");
        }
    }

    @Nested
    @DisplayName("Starting Batch")
    class StartTests {

        @Test
        @DisplayName("start() should transition to IN_PROGRESS and set startedAt")
        void startBatchSuccessfully() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            Instant beforeStart = Instant.now();

            // When
            batch.start();

            // Then
            assertThat(batch.status()).isEqualTo(BatchStatus.IN_PROGRESS);
            assertThat(batch.startedAt()).isNotNull();
            assertThat(batch.startedAt()).isAfterOrEqualTo(beforeStart);
        }

        @Test
        @DisplayName("start() should fail when batch has no items")
        void startFailsWhenNoItems() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);

            // When/Then
            assertThatThrownBy(() -> batch.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Cannot start batch with no items");
        }

        @Test
        @DisplayName("start() should fail when batch is not PENDING")
        void startFailsWhenNotPending() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.start();

            // When/Then
            assertThatThrownBy(() -> batch.start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only start PENDING batches");
        }
    }

    @Nested
    @DisplayName("Incrementing Counts")
    class IncrementCountTests {

        @Test
        @DisplayName("incrementSuccess() should increase success count")
        void incrementSuccessCount() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.addItem("data-2");
            batch.start();

            // When
            batch.incrementSuccess();
            batch.incrementSuccess();

            // Then
            assertThat(batch.successCount()).isEqualTo(2);
            assertThat(batch.failedCount()).isZero();
        }

        @Test
        @DisplayName("incrementSuccess() should fail when batch is not IN_PROGRESS")
        void incrementSuccessFailsWhenNotInProgress() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");

            // When/Then
            assertThatThrownBy(() -> batch.incrementSuccess())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only update counts for IN_PROGRESS batches");
        }

        @Test
        @DisplayName("incrementFailed() should increase failed count")
        void incrementFailedCount() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.addItem("data-2");
            batch.start();

            // When
            batch.incrementFailed();
            batch.incrementFailed();

            // Then
            assertThat(batch.failedCount()).isEqualTo(2);
            assertThat(batch.successCount()).isZero();
        }

        @Test
        @DisplayName("incrementFailed() should fail when batch is not IN_PROGRESS")
        void incrementFailedFailsWhenNotInProgress() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");

            // When/Then
            assertThatThrownBy(() -> batch.incrementFailed())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only update counts for IN_PROGRESS batches");
        }
    }

    @Nested
    @DisplayName("Completing Batch")
    class CompleteTests {

        @Test
        @DisplayName("complete() should set status to COMPLETED when all items succeeded")
        void completeWithAllSuccess() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.addItem("data-2");
            batch.start();
            batch.incrementSuccess();
            batch.incrementSuccess();
            Instant beforeComplete = Instant.now();

            // When
            batch.complete();

            // Then
            assertThat(batch.status()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(batch.completedAt()).isNotNull();
            assertThat(batch.completedAt()).isAfterOrEqualTo(beforeComplete);
            assertThat(batch.successCount()).isEqualTo(2);
            assertThat(batch.failedCount()).isZero();
        }

        @Test
        @DisplayName("complete() should set status to COMPLETED_WITH_ERRORS when some items failed")
        void completeWithPartialFailure() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.addItem("data-2");
            batch.addItem("data-3");
            batch.start();
            batch.incrementSuccess();
            batch.incrementSuccess();
            batch.incrementFailed();

            // When
            batch.complete();

            // Then
            assertThat(batch.status()).isEqualTo(BatchStatus.COMPLETED_WITH_ERRORS);
            assertThat(batch.successCount()).isEqualTo(2);
            assertThat(batch.failedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("complete() should set status to FAILED when all items failed")
        void completeWithAllFailures() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.addItem("data-2");
            batch.start();
            batch.incrementFailed();
            batch.incrementFailed();

            // When
            batch.complete();

            // Then
            assertThat(batch.status()).isEqualTo(BatchStatus.FAILED);
            assertThat(batch.successCount()).isZero();
            assertThat(batch.failedCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("complete() should fail when batch is not IN_PROGRESS")
        void completeFailsWhenNotInProgress() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");

            // When/Then
            assertThatThrownBy(() -> batch.complete())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only complete IN_PROGRESS batches");
        }
    }

    @Nested
    @DisplayName("Failing Batch")
    class FailTests {

        @Test
        @DisplayName("fail() should set status to FAILED and set completedAt")
        void failBatch() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.start();
            Instant beforeFail = Instant.now();

            // When
            batch.fail("Batch level error occurred");

            // Then
            assertThat(batch.status()).isEqualTo(BatchStatus.FAILED);
            assertThat(batch.completedAt()).isNotNull();
            assertThat(batch.completedAt()).isAfterOrEqualTo(beforeFail);
        }

        @Test
        @DisplayName("fail() should work from any state")
        void failFromAnyState() {
            // PENDING state
            Batch batch1 = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch1.addItem("data-1");
            batch1.fail("Critical error");
            assertThat(batch1.status()).isEqualTo(BatchStatus.FAILED);

            // IN_PROGRESS state
            Batch batch2 = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch2.addItem("data-1");
            batch2.start();
            batch2.fail("Critical error");
            assertThat(batch2.status()).isEqualTo(BatchStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("Pending Item Operations")
    class PendingItemTests {

        @Test
        @DisplayName("getNextPendingItem() should return first PENDING item")
        void getNextPendingItem() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.addItem("data-2");
            batch.addItem("data-3");
            batch.start();

            // When
            Batch.BatchItem nextItem = batch.getNextPendingItem();

            // Then
            assertThat(nextItem).isNotNull();
            assertThat(nextItem.sequenceNumber()).isEqualTo(1);
            assertThat(nextItem.status()).isEqualTo(BatchItemStatus.PENDING);
        }

        @Test
        @DisplayName("getNextPendingItem() should skip IN_PROGRESS items")
        void getNextPendingItemSkipsInProgress() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.addItem("data-2");
            batch.start();
            batch.items().get(0).markInProgress();

            // When
            Batch.BatchItem nextItem = batch.getNextPendingItem();

            // Then
            assertThat(nextItem).isNotNull();
            assertThat(nextItem.sequenceNumber()).isEqualTo(2);
        }

        @Test
        @DisplayName("getNextPendingItem() should return null when no PENDING items")
        void getNextPendingItemReturnsNullWhenNoPending() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.start();
            batch.items().get(0).markInProgress();
            batch.items().get(0).markSuccess("result");

            // When
            Batch.BatchItem nextItem = batch.getNextPendingItem();

            // Then
            assertThat(nextItem).isNull();
        }

        @Test
        @DisplayName("pendingCount() should return correct count")
        void pendingCount() {
            // Given
            Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, TEST_PROFILE_ID, TEST_USER);
            batch.addItem("data-1");
            batch.addItem("data-2");
            batch.addItem("data-3");
            batch.addItem("data-4");
            batch.start();

            // When/Then - Initially all pending
            assertThat(batch.pendingCount()).isEqualTo(4);

            // Process some items
            batch.incrementSuccess();
            assertThat(batch.pendingCount()).isEqualTo(3);

            batch.incrementFailed();
            assertThat(batch.pendingCount()).isEqualTo(2);

            batch.incrementSuccess();
            batch.incrementSuccess();
            assertThat(batch.pendingCount()).isZero();
        }
    }

    @Nested
    @DisplayName("Reconstitution")
    class ReconstituteTests {

        @Test
        @DisplayName("reconstitute() should restore batch from persistence")
        void reconstituteBatch() {
            // Given
            BatchId batchId = BatchId.generate();
            Instant createdAt = Instant.now().minusSeconds(3600);
            Instant startedAt = Instant.now().minusSeconds(1800);
            Instant completedAt = Instant.now().minusSeconds(900);

            BatchItemId itemId = BatchItemId.generate();
            Batch.BatchItem item = new Batch.BatchItem(
                    itemId, 1, "input-data",
                    BatchItemStatus.SUCCESS, "result-data", null,
                    Instant.now().minusSeconds(1000)
            );

            List<Batch.BatchItem> items = new ArrayList<>();
            items.add(item);

            // When
            Batch batch = Batch.reconstitute(
                    batchId,
                    BatchType.PAYOR_ENROLMENT,
                    TEST_PROFILE_ID,
                    BatchStatus.COMPLETED,
                    1,
                    1,
                    0,
                    items,
                    createdAt,
                    TEST_USER,
                    startedAt,
                    completedAt
            );

            // Then
            assertThat(batch.id()).isEqualTo(batchId);
            assertThat(batch.type()).isEqualTo(BatchType.PAYOR_ENROLMENT);
            assertThat(batch.sourceProfileId()).isEqualTo(TEST_PROFILE_ID);
            assertThat(batch.status()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(batch.totalItems()).isEqualTo(1);
            assertThat(batch.successCount()).isEqualTo(1);
            assertThat(batch.failedCount()).isZero();
            assertThat(batch.items()).hasSize(1);
            assertThat(batch.createdBy()).isEqualTo(TEST_USER);
            assertThat(batch.startedAt()).isEqualTo(startedAt);
            assertThat(batch.completedAt()).isEqualTo(completedAt);
        }

        @Test
        @DisplayName("reconstitute() should handle batch with multiple items in different states")
        void reconstituteBatchWithMultipleItems() {
            // Given
            List<Batch.BatchItem> items = new ArrayList<>();
            items.add(new Batch.BatchItem(BatchItemId.generate(), 1, "data-1",
                    BatchItemStatus.SUCCESS, "result-1", null, Instant.now()));
            items.add(new Batch.BatchItem(BatchItemId.generate(), 2, "data-2",
                    BatchItemStatus.FAILED, null, "error", Instant.now()));
            items.add(new Batch.BatchItem(BatchItemId.generate(), 3, "data-3",
                    BatchItemStatus.PENDING, null, null, null));

            // When
            Batch batch = Batch.reconstitute(
                    BatchId.generate(),
                    BatchType.PAYOR_ENROLMENT,
                    TEST_PROFILE_ID,
                    BatchStatus.IN_PROGRESS,
                    3,
                    1,
                    1,
                    items,
                    Instant.now(),
                    TEST_USER,
                    Instant.now(),
                    null
            );

            // Then
            assertThat(batch.items()).hasSize(3);
            assertThat(batch.totalItems()).isEqualTo(3);
            assertThat(batch.successCount()).isEqualTo(1);
            assertThat(batch.failedCount()).isEqualTo(1);
            assertThat(batch.pendingCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("BatchItem Tests")
    class BatchItemTests {

        @Test
        @DisplayName("BatchItem constructor should create item with PENDING status")
        void createBatchItem() {
            // When
            Batch.BatchItem item = new Batch.BatchItem(1, "test-data");

            // Then
            assertThat(item.id()).isNotNull();
            assertThat(item.sequenceNumber()).isEqualTo(1);
            assertThat(item.inputData()).isEqualTo("test-data");
            assertThat(item.status()).isEqualTo(BatchItemStatus.PENDING);
            assertThat(item.resultData()).isNull();
            assertThat(item.errorMessage()).isNull();
            assertThat(item.processedAt()).isNull();
        }

        @Test
        @DisplayName("BatchItem constructor should reject null input data")
        void createBatchItemRejectsNullData() {
            assertThatThrownBy(() -> new Batch.BatchItem(1, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("inputData cannot be null");
        }

        @Test
        @DisplayName("markInProgress() should transition from PENDING to IN_PROGRESS")
        void markInProgress() {
            // Given
            Batch.BatchItem item = new Batch.BatchItem(1, "test-data");

            // When
            item.markInProgress();

            // Then
            assertThat(item.status()).isEqualTo(BatchItemStatus.IN_PROGRESS);
        }

        @Test
        @DisplayName("markInProgress() should fail when not PENDING")
        void markInProgressFailsWhenNotPending() {
            // Given
            Batch.BatchItem item = new Batch.BatchItem(1, "test-data");
            item.markInProgress();

            // When/Then
            assertThatThrownBy(() -> item.markInProgress())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only start processing PENDING items");
        }

        @Test
        @DisplayName("markSuccess() should transition from IN_PROGRESS to SUCCESS")
        void markSuccess() {
            // Given
            Batch.BatchItem item = new Batch.BatchItem(1, "test-data");
            item.markInProgress();
            Instant beforeSuccess = Instant.now();

            // When
            item.markSuccess("result-data");

            // Then
            assertThat(item.status()).isEqualTo(BatchItemStatus.SUCCESS);
            assertThat(item.resultData()).isEqualTo("result-data");
            assertThat(item.processedAt()).isNotNull();
            assertThat(item.processedAt()).isAfterOrEqualTo(beforeSuccess);
            assertThat(item.errorMessage()).isNull();
        }

        @Test
        @DisplayName("markSuccess() should fail when not IN_PROGRESS")
        void markSuccessFailsWhenNotInProgress() {
            // Given
            Batch.BatchItem item = new Batch.BatchItem(1, "test-data");

            // When/Then
            assertThatThrownBy(() -> item.markSuccess("result"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only mark IN_PROGRESS items as success");
        }

        @Test
        @DisplayName("markFailed() should transition from IN_PROGRESS to FAILED")
        void markFailed() {
            // Given
            Batch.BatchItem item = new Batch.BatchItem(1, "test-data");
            item.markInProgress();
            Instant beforeFail = Instant.now();

            // When
            item.markFailed("error-message");

            // Then
            assertThat(item.status()).isEqualTo(BatchItemStatus.FAILED);
            assertThat(item.errorMessage()).isEqualTo("error-message");
            assertThat(item.processedAt()).isNotNull();
            assertThat(item.processedAt()).isAfterOrEqualTo(beforeFail);
            assertThat(item.resultData()).isNull();
        }

        @Test
        @DisplayName("markFailed() should fail when not IN_PROGRESS")
        void markFailedFailsWhenNotInProgress() {
            // Given
            Batch.BatchItem item = new Batch.BatchItem(1, "test-data");

            // When/Then
            assertThatThrownBy(() -> item.markFailed("error"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Can only mark IN_PROGRESS items as failed");
        }

        @Test
        @DisplayName("BatchItem reconstitution constructor should restore all fields")
        void reconstituteBatchItem() {
            // Given
            BatchItemId itemId = BatchItemId.generate();
            Instant processedAt = Instant.now();

            // When
            Batch.BatchItem item = new Batch.BatchItem(
                    itemId, 5, "input-data",
                    BatchItemStatus.SUCCESS, "result-data", null, processedAt
            );

            // Then
            assertThat(item.id()).isEqualTo(itemId);
            assertThat(item.sequenceNumber()).isEqualTo(5);
            assertThat(item.inputData()).isEqualTo("input-data");
            assertThat(item.status()).isEqualTo(BatchItemStatus.SUCCESS);
            assertThat(item.resultData()).isEqualTo("result-data");
            assertThat(item.errorMessage()).isNull();
            assertThat(item.processedAt()).isEqualTo(processedAt);
        }

        @Test
        @DisplayName("BatchItem reconstitution should reject null ID")
        void reconstituteBatchItemRejectsNullId() {
            assertThatThrownBy(() -> new Batch.BatchItem(
                    null, 1, "data", BatchItemStatus.PENDING, null, null, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("id cannot be null");
        }
    }
}
