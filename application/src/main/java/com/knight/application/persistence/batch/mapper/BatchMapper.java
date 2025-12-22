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
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Maps between Batch domain aggregate and JPA entities.
 */
@Component
public class BatchMapper {

    /**
     * Convert domain aggregate to JPA entity.
     */
    public BatchEntity toEntity(Batch batch) {
        BatchEntity entity = new BatchEntity();
        entity.setBatchId(batch.id().value());
        entity.setBatchType(batch.type().name());
        entity.setSourceProfileId(batch.sourceProfileId().urn());
        entity.setStatus(batch.status().name());
        entity.setTotalItems(batch.totalItems());
        entity.setSuccessCount(batch.successCount());
        entity.setFailedCount(batch.failedCount());
        entity.setCreatedAt(batch.createdAt());
        entity.setCreatedBy(batch.createdBy());
        entity.setStartedAt(batch.startedAt());
        entity.setCompletedAt(batch.completedAt());

        // Map items
        for (Batch.BatchItem item : batch.items()) {
            BatchItemEntity itemEntity = toItemEntity(item);
            entity.addItem(itemEntity);
        }

        return entity;
    }

    /**
     * Convert JPA entity to domain aggregate.
     */
    public Batch toDomain(BatchEntity entity) {
        List<Batch.BatchItem> items = entity.getItems().stream()
                .map(this::toItemDomain)
                .collect(Collectors.toList());

        return Batch.reconstitute(
                BatchId.of(entity.getBatchId()),
                BatchType.valueOf(entity.getBatchType()),
                ProfileId.fromUrn(entity.getSourceProfileId()),
                BatchStatus.valueOf(entity.getStatus()),
                entity.getTotalItems(),
                entity.getSuccessCount(),
                entity.getFailedCount(),
                items,
                entity.getCreatedAt(),
                entity.getCreatedBy(),
                entity.getStartedAt(),
                entity.getCompletedAt()
        );
    }

    private BatchItemEntity toItemEntity(Batch.BatchItem item) {
        BatchItemEntity entity = new BatchItemEntity();
        entity.setBatchItemId(item.id().value());
        entity.setSequenceNumber(item.sequenceNumber());
        entity.setInputData(item.inputData());
        entity.setStatus(item.status().name());
        entity.setResultData(item.resultData());
        entity.setErrorMessage(item.errorMessage());
        entity.setProcessedAt(item.processedAt());
        return entity;
    }

    private Batch.BatchItem toItemDomain(BatchItemEntity entity) {
        return new Batch.BatchItem(
                BatchItemId.of(entity.getBatchItemId()),
                entity.getSequenceNumber(),
                entity.getInputData(),
                BatchItemStatus.valueOf(entity.getStatus()),
                entity.getResultData(),
                entity.getErrorMessage(),
                entity.getProcessedAt()
        );
    }
}
