package com.knight.application.rest.batch;

import com.knight.application.rest.batch.dto.*;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.service.PayorEnrolmentService;
import com.knight.domain.batch.types.BatchItemResult;
import com.knight.domain.batch.types.PayorEnrolmentRequest;
import com.knight.platform.sharedkernel.BatchId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for batch status and item queries.
 */
@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private static final Logger logger = LoggerFactory.getLogger(BatchController.class);

    private final PayorEnrolmentService payorEnrolmentService;
    private final ObjectMapper objectMapper;

    public BatchController(PayorEnrolmentService payorEnrolmentService, ObjectMapper objectMapper) {
        this.payorEnrolmentService = payorEnrolmentService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get batch details by ID.
     */
    @GetMapping("/{batchId}")
    public ResponseEntity<BatchDetailDto> getBatch(@PathVariable String batchId) {
        logger.info("Getting batch details for {}", batchId);

        Batch batch = payorEnrolmentService.getBatch(BatchId.of(batchId))
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        return ResponseEntity.ok(toDetailDto(batch));
    }

    /**
     * Get batch items, optionally filtered by status.
     */
    @GetMapping("/{batchId}/items")
    public ResponseEntity<List<BatchItemDto>> getBatchItems(
            @PathVariable String batchId,
            @RequestParam(required = false) String status
    ) {
        logger.info("Getting batch items for {} with status filter: {}", batchId, status);

        Batch batch = payorEnrolmentService.getBatch(BatchId.of(batchId))
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        List<BatchItemDto> items = batch.items().stream()
                .filter(item -> status == null || item.status().name().equalsIgnoreCase(status))
                .map(this::toItemDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(items);
    }

    // ========== Helper Methods ==========

    private BatchDetailDto toDetailDto(Batch batch) {
        return new BatchDetailDto(
                batch.id().toString(),
                batch.type().name(),
                batch.type().displayName(),
                batch.sourceProfileId().urn(),
                batch.status().name(),
                batch.status().displayName(),
                batch.totalItems(),
                batch.successCount(),
                batch.failedCount(),
                batch.pendingCount(),
                batch.createdAt(),
                batch.createdBy(),
                batch.startedAt(),
                batch.completedAt()
        );
    }

    private BatchItemDto toItemDto(Batch.BatchItem item) {
        // Extract business name from input data
        String businessName = extractBusinessName(item.inputData());

        // Parse result if success
        BatchItemDto.BatchItemResultDto resultDto = null;
        if (item.resultData() != null) {
            try {
                BatchItemResult result = objectMapper.readValue(item.resultData(), BatchItemResult.class);
                resultDto = new BatchItemDto.BatchItemResultDto(
                        result.indirectClientId(),
                        result.profileId(),
                        result.userIds()
                );
            } catch (Exception e) {
                logger.warn("Failed to parse result data for item {}", item.id(), e);
            }
        }

        return new BatchItemDto(
                item.id().toString(),
                item.sequenceNumber(),
                businessName,
                item.status().name(),
                item.status().displayName(),
                resultDto,
                item.errorMessage(),
                item.processedAt()
        );
    }

    private String extractBusinessName(String inputData) {
        try {
            PayorEnrolmentRequest request = objectMapper.readValue(inputData, PayorEnrolmentRequest.class);
            return request.businessName();
        } catch (Exception e) {
            return "Unknown";
        }
    }
}
