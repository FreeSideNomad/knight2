package com.knight.application.persistence.batch.mapper;

import com.knight.application.persistence.batch.entity.BatchEntity;
import com.knight.application.persistence.batch.entity.BatchItemEntity;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.types.BatchItemStatus;
import com.knight.domain.batch.types.BatchStatus;
import com.knight.domain.batch.types.BatchType;
import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.BatchItemId;
import com.knight.platform.sharedkernel.ProfileId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BatchMapper.
 * Tests domain to entity and entity to domain mapping, including batch items.
 */
class BatchMapperTest {

    private BatchMapper mapper;

    private static final ProfileId SOURCE_PROFILE_ID = ProfileId.fromUrn("servicing:srf:123456789");
    private static final String CREATED_BY = "admin@example.com";

    @BeforeEach
    void setUp() {
        mapper = new BatchMapper();
    }

    // ==================== Domain to Entity ====================

    @Nested
    @DisplayName("Domain to Entity Mapping")
    class DomainToEntityTests {

        @Test
        @DisplayName("should map all batch fields from domain to entity")
        void shouldMapAllBatchFieldsFromDomainToEntity() {
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            BatchEntity entity = mapper.toEntity(batch);

            assertThat(entity).isNotNull();
            assertThat(entity.getBatchId()).isNotNull();
            assertThat(entity.getBatchType()).isEqualTo("PAYOR_ENROLMENT");
            assertThat(entity.getSourceProfileId()).isEqualTo(SOURCE_PROFILE_ID.urn());
            assertThat(entity.getStatus()).isEqualTo("PENDING");
            assertThat(entity.getTotalItems()).isEqualTo(0);
            assertThat(entity.getSuccessCount()).isEqualTo(0);
            assertThat(entity.getFailedCount()).isEqualTo(0);
            assertThat(entity.getCreatedAt()).isNotNull();
            assertThat(entity.getCreatedBy()).isEqualTo(CREATED_BY);
            assertThat(entity.getStartedAt()).isNull();
            assertThat(entity.getCompletedAt()).isNull();
        }

        @Test
        @DisplayName("should map batch with items from domain to entity")
        void shouldMapBatchWithItemsFromDomainToEntity() {
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            batch.addItem("{\"name\": \"Client 1\"}");
            batch.addItem("{\"name\": \"Client 2\"}");
            batch.addItem("{\"name\": \"Client 3\"}");

            BatchEntity entity = mapper.toEntity(batch);

            assertThat(entity.getItems()).hasSize(3);
            assertThat(entity.getTotalItems()).isEqualTo(3);

            BatchItemEntity item1 = entity.getItems().get(0);
            assertThat(item1.getSequenceNumber()).isEqualTo(1);
            assertThat(item1.getInputData()).isEqualTo("{\"name\": \"Client 1\"}");
            assertThat(item1.getStatus()).isEqualTo("PENDING");
            assertThat(item1.getResultData()).isNull();
            assertThat(item1.getErrorMessage()).isNull();
            assertThat(item1.getProcessedAt()).isNull();
            assertThat(item1.getBatch()).isEqualTo(entity);
        }

        @Test
        @DisplayName("should map in-progress batch from domain to entity")
        void shouldMapInProgressBatchFromDomainToEntity() {
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            batch.addItem("{\"accountNumber\": \"123\"}");
            batch.start();

            BatchEntity entity = mapper.toEntity(batch);

            assertThat(entity.getStatus()).isEqualTo("IN_PROGRESS");
            assertThat(entity.getStartedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map completed batch from domain to entity")
        void shouldMapCompletedBatchFromDomainToEntity() {
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            batch.addItem("{\"name\": \"Client 1\"}");
            batch.start();
            batch.incrementSuccess();
            batch.complete();

            BatchEntity entity = mapper.toEntity(batch);

            assertThat(entity.getStatus()).isEqualTo("COMPLETED");
            assertThat(entity.getSuccessCount()).isEqualTo(1);
            assertThat(entity.getFailedCount()).isEqualTo(0);
            assertThat(entity.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map batch with processed items")
        void shouldMapBatchWithProcessedItems() {
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            Batch.BatchItem item = batch.addItem("{\"name\": \"Test Client\"}");
            batch.start();
            item.markInProgress();
            item.markSuccess("{\"clientId\": \"srf:123\"}");

            BatchEntity entity = mapper.toEntity(batch);

            assertThat(entity.getItems()).hasSize(1);
            BatchItemEntity itemEntity = entity.getItems().get(0);
            assertThat(itemEntity.getStatus()).isEqualTo("SUCCESS");
            assertThat(itemEntity.getResultData()).isEqualTo("{\"clientId\": \"srf:123\"}");
            assertThat(itemEntity.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map batch with failed items")
        void shouldMapBatchWithFailedItems() {
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            Batch.BatchItem item = batch.addItem("{\"name\": \"Invalid Client\"}");
            batch.start();
            item.markInProgress();
            item.markFailed("Validation error: name is required");

            BatchEntity entity = mapper.toEntity(batch);

            BatchItemEntity itemEntity = entity.getItems().get(0);
            assertThat(itemEntity.getStatus()).isEqualTo("FAILED");
            assertThat(itemEntity.getErrorMessage()).isEqualTo("Validation error: name is required");
            assertThat(itemEntity.getResultData()).isNull();
            assertThat(itemEntity.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map batch with mixed item statuses")
        void shouldMapBatchWithMixedItemStatuses() {
            Batch batch = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            Batch.BatchItem item1 = batch.addItem("{\"name\": \"Client 1\"}");
            Batch.BatchItem item2 = batch.addItem("{\"name\": \"Client 2\"}");
            Batch.BatchItem item3 = batch.addItem("{\"name\": \"Client 3\"}");

            batch.start();
            item1.markInProgress();
            item1.markSuccess("{\"clientId\": \"srf:123\"}");
            item2.markInProgress();
            item2.markFailed("Error processing");
            // item3 remains pending

            BatchEntity entity = mapper.toEntity(batch);

            assertThat(entity.getItems()).hasSize(3);
            assertThat(entity.getItems().get(0).getStatus()).isEqualTo("SUCCESS");
            assertThat(entity.getItems().get(1).getStatus()).isEqualTo("FAILED");
            assertThat(entity.getItems().get(2).getStatus()).isEqualTo("PENDING");
        }
    }

    // ==================== Entity to Domain ====================

    @Nested
    @DisplayName("Entity to Domain Mapping")
    class EntityToDomainTests {

        @Test
        @DisplayName("should map all batch fields from entity to domain")
        void shouldMapAllBatchFieldsFromEntityToDomain() {
            BatchEntity entity = createBatchEntity();

            Batch batch = mapper.toDomain(entity);

            assertThat(batch).isNotNull();
            assertThat(batch.id().value()).isEqualTo(entity.getBatchId());
            assertThat(batch.type()).isEqualTo(BatchType.PAYOR_ENROLMENT);
            assertThat(batch.sourceProfileId().urn()).isEqualTo(SOURCE_PROFILE_ID.urn());
            assertThat(batch.status()).isEqualTo(BatchStatus.PENDING);
            assertThat(batch.totalItems()).isEqualTo(0);
            assertThat(batch.successCount()).isEqualTo(0);
            assertThat(batch.failedCount()).isEqualTo(0);
            assertThat(batch.createdBy()).isEqualTo(CREATED_BY);
            assertThat(batch.startedAt()).isNull();
            assertThat(batch.completedAt()).isNull();
        }

        @Test
        @DisplayName("should map batch with items from entity to domain")
        void shouldMapBatchWithItemsFromEntityToDomain() {
            BatchEntity entity = createBatchEntity();
            entity.setTotalItems(2);

            BatchItemEntity item1 = new BatchItemEntity();
            item1.setBatchItemId(UUID.randomUUID());
            item1.setBatch(entity);
            item1.setSequenceNumber(1);
            item1.setInputData("{\"name\": \"Client 1\"}");
            item1.setStatus("PENDING");
            entity.getItems().add(item1);

            BatchItemEntity item2 = new BatchItemEntity();
            item2.setBatchItemId(UUID.randomUUID());
            item2.setBatch(entity);
            item2.setSequenceNumber(2);
            item2.setInputData("{\"name\": \"Client 2\"}");
            item2.setStatus("PENDING");
            entity.getItems().add(item2);

            Batch batch = mapper.toDomain(entity);

            assertThat(batch.items()).hasSize(2);
            assertThat(batch.totalItems()).isEqualTo(2);

            Batch.BatchItem domainItem1 = batch.items().get(0);
            assertThat(domainItem1.sequenceNumber()).isEqualTo(1);
            assertThat(domainItem1.inputData()).isEqualTo("{\"name\": \"Client 1\"}");
            assertThat(domainItem1.status()).isEqualTo(BatchItemStatus.PENDING);
        }

        @Test
        @DisplayName("should map all batch statuses from entity to domain")
        void shouldMapAllBatchStatusesFromEntityToDomain() {
            BatchEntity entity = createBatchEntity();

            entity.setStatus("PENDING");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(BatchStatus.PENDING);

            entity.setStatus("IN_PROGRESS");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(BatchStatus.IN_PROGRESS);

            entity.setStatus("COMPLETED");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(BatchStatus.COMPLETED);

            entity.setStatus("COMPLETED_WITH_ERRORS");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(BatchStatus.COMPLETED_WITH_ERRORS);

            entity.setStatus("FAILED");
            assertThat(mapper.toDomain(entity).status()).isEqualTo(BatchStatus.FAILED);
        }

        @Test
        @DisplayName("should map all batch item statuses from entity to domain")
        void shouldMapAllBatchItemStatusesFromEntityToDomain() {
            BatchEntity entity = createBatchEntity();
            entity.setTotalItems(1);

            BatchItemEntity itemEntity = new BatchItemEntity();
            itemEntity.setBatchItemId(UUID.randomUUID());
            itemEntity.setBatch(entity);
            itemEntity.setSequenceNumber(1);
            itemEntity.setInputData("{}");

            itemEntity.setStatus("PENDING");
            entity.getItems().clear();
            entity.getItems().add(itemEntity);
            assertThat(mapper.toDomain(entity).items().get(0).status()).isEqualTo(BatchItemStatus.PENDING);

            itemEntity.setStatus("IN_PROGRESS");
            assertThat(mapper.toDomain(entity).items().get(0).status()).isEqualTo(BatchItemStatus.IN_PROGRESS);

            itemEntity.setStatus("SUCCESS");
            assertThat(mapper.toDomain(entity).items().get(0).status()).isEqualTo(BatchItemStatus.SUCCESS);

            itemEntity.setStatus("FAILED");
            assertThat(mapper.toDomain(entity).items().get(0).status()).isEqualTo(BatchItemStatus.FAILED);
        }

        @Test
        @DisplayName("should map completed batch from entity to domain")
        void shouldMapCompletedBatchFromEntityToDomain() {
            BatchEntity entity = createBatchEntity();
            entity.setStatus("COMPLETED");
            entity.setTotalItems(1);
            entity.setSuccessCount(1);
            entity.setFailedCount(0);
            entity.setStartedAt(Instant.now().minusSeconds(60));
            entity.setCompletedAt(Instant.now());

            Batch batch = mapper.toDomain(entity);

            assertThat(batch.status()).isEqualTo(BatchStatus.COMPLETED);
            assertThat(batch.successCount()).isEqualTo(1);
            assertThat(batch.failedCount()).isEqualTo(0);
            assertThat(batch.startedAt()).isNotNull();
            assertThat(batch.completedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map processed item from entity to domain")
        void shouldMapProcessedItemFromEntityToDomain() {
            BatchEntity entity = createBatchEntity();
            entity.setTotalItems(1);

            BatchItemEntity itemEntity = new BatchItemEntity();
            itemEntity.setBatchItemId(UUID.randomUUID());
            itemEntity.setBatch(entity);
            itemEntity.setSequenceNumber(1);
            itemEntity.setInputData("{\"name\": \"Test\"}");
            itemEntity.setStatus("SUCCESS");
            itemEntity.setResultData("{\"clientId\": \"srf:123\"}");
            itemEntity.setProcessedAt(Instant.now());
            entity.getItems().add(itemEntity);

            Batch batch = mapper.toDomain(entity);

            Batch.BatchItem item = batch.items().get(0);
            assertThat(item.status()).isEqualTo(BatchItemStatus.SUCCESS);
            assertThat(item.resultData()).isEqualTo("{\"clientId\": \"srf:123\"}");
            assertThat(item.processedAt()).isNotNull();
        }

        @Test
        @DisplayName("should map failed item from entity to domain")
        void shouldMapFailedItemFromEntityToDomain() {
            BatchEntity entity = createBatchEntity();
            entity.setTotalItems(1);

            BatchItemEntity itemEntity = new BatchItemEntity();
            itemEntity.setBatchItemId(UUID.randomUUID());
            itemEntity.setBatch(entity);
            itemEntity.setSequenceNumber(1);
            itemEntity.setInputData("{\"name\": \"Test\"}");
            itemEntity.setStatus("FAILED");
            itemEntity.setErrorMessage("Validation failed");
            itemEntity.setProcessedAt(Instant.now());
            entity.getItems().add(itemEntity);

            Batch batch = mapper.toDomain(entity);

            Batch.BatchItem item = batch.items().get(0);
            assertThat(item.status()).isEqualTo(BatchItemStatus.FAILED);
            assertThat(item.errorMessage()).isEqualTo("Validation failed");
            assertThat(item.processedAt()).isNotNull();
        }
    }

    // ==================== Round-trip ====================

    @Nested
    @DisplayName("Round-trip Mapping")
    class RoundTripTests {

        @Test
        @DisplayName("should preserve all batch data in round-trip conversion")
        void shouldPreserveAllBatchDataInRoundTrip() {
            Batch original = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            original.addItem("{\"account\": \"1\"}");
            original.addItem("{\"account\": \"2\"}");

            // Round-trip: Domain -> Entity -> Domain
            BatchEntity entity = mapper.toEntity(original);
            Batch reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.type()).isEqualTo(original.type());
            assertThat(reconstructed.sourceProfileId().urn()).isEqualTo(original.sourceProfileId().urn());
            assertThat(reconstructed.status()).isEqualTo(original.status());
            assertThat(reconstructed.totalItems()).isEqualTo(original.totalItems());
            assertThat(reconstructed.items()).hasSameSizeAs(original.items());
        }

        @Test
        @DisplayName("should preserve item data in round-trip conversion")
        void shouldPreserveItemDataInRoundTrip() {
            Batch original = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            Batch.BatchItem item = original.addItem("{\"name\": \"Test\"}");
            original.start();
            item.markInProgress();
            item.markSuccess("{\"result\": \"ok\"}");

            BatchEntity entity = mapper.toEntity(original);
            Batch reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.items()).hasSize(1);
            Batch.BatchItem reconstructedItem = reconstructed.items().get(0);
            assertThat(reconstructedItem.inputData()).isEqualTo(item.inputData());
            assertThat(reconstructedItem.status()).isEqualTo(item.status());
            assertThat(reconstructedItem.resultData()).isEqualTo(item.resultData());
        }

        @Test
        @DisplayName("should preserve completed batch state in round-trip")
        void shouldPreserveCompletedBatchStateInRoundTrip() {
            Batch original = Batch.create(
                BatchType.PAYOR_ENROLMENT,
                SOURCE_PROFILE_ID,
                CREATED_BY
            );

            Batch.BatchItem item1 = original.addItem("{\"name\": \"Client 1\"}");
            Batch.BatchItem item2 = original.addItem("{\"name\": \"Client 2\"}");

            original.start();
            item1.markInProgress();
            item1.markSuccess("{\"id\": \"1\"}");
            original.incrementSuccess();
            item2.markInProgress();
            item2.markFailed("Error");
            original.incrementFailed();
            original.complete();

            BatchEntity entity = mapper.toEntity(original);
            Batch reconstructed = mapper.toDomain(entity);

            assertThat(reconstructed.status()).isEqualTo(BatchStatus.COMPLETED_WITH_ERRORS);
            assertThat(reconstructed.successCount()).isEqualTo(1);
            assertThat(reconstructed.failedCount()).isEqualTo(1);
            assertThat(reconstructed.completedAt()).isNotNull();
        }
    }

    // ==================== Helper Methods ====================

    private BatchEntity createBatchEntity() {
        BatchEntity entity = new BatchEntity();
        entity.setBatchId(UUID.randomUUID());
        entity.setBatchType("PAYOR_ENROLMENT");
        entity.setSourceProfileId(SOURCE_PROFILE_ID.urn());
        entity.setStatus("PENDING");
        entity.setTotalItems(0);
        entity.setSuccessCount(0);
        entity.setFailedCount(0);
        entity.setCreatedAt(Instant.now());
        entity.setCreatedBy(CREATED_BY);
        return entity;
    }
}
