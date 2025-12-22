package com.knight.application.rest.batch;

import com.knight.application.rest.batch.dto.*;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.service.PayorEnrolmentService;
import com.knight.domain.batch.types.BatchItemResult;
import com.knight.domain.batch.types.ValidationError;
import com.knight.domain.batch.types.ValidationResult;
import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.ProfileId;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for payor enrolment batch operations.
 */
@RestController
@RequestMapping("/api/profiles/{profileId}/payor-enrolment")
public class PayorEnrolmentController {

    private static final Logger logger = LoggerFactory.getLogger(PayorEnrolmentController.class);
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB

    private final PayorEnrolmentService payorEnrolmentService;
    private final ObjectMapper objectMapper;

    public PayorEnrolmentController(PayorEnrolmentService payorEnrolmentService, ObjectMapper objectMapper) {
        this.payorEnrolmentService = payorEnrolmentService;
        this.objectMapper = objectMapper;
    }

    /**
     * Validate a payor enrolment JSON file.
     * This is synchronous and returns validation results immediately.
     */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ValidationResultDto> validate(
            @PathVariable String profileId,
            @RequestParam("file") MultipartFile file,
            Principal principal
    ) {
        String requestedBy = principal != null ? principal.getName() : "system";
        logger.info("Validating payor enrolment file for profile {} by {}", profileId, requestedBy);

        // Validate file
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(
                    new ValidationResultDto(false, 0, List.of(
                            new ValidationResultDto.ValidationErrorDto(0, null, "file", "File is empty")
                    ), null)
            );
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            return ResponseEntity.badRequest().body(
                    new ValidationResultDto(false, 0, List.of(
                            new ValidationResultDto.ValidationErrorDto(0, null, "file", "File exceeds maximum size of 5MB")
                    ), null)
            );
        }

        try {
            String jsonContent = new String(file.getBytes(), StandardCharsets.UTF_8);
            ProfileId profId = ProfileId.fromUrn(profileId);

            ValidationResult result = payorEnrolmentService.validate(profId, jsonContent, requestedBy);

            return ResponseEntity.ok(toDto(result));
        } catch (IOException e) {
            logger.error("Failed to read file", e);
            return ResponseEntity.badRequest().body(
                    new ValidationResultDto(false, 0, List.of(
                            new ValidationResultDto.ValidationErrorDto(0, null, "file", "Failed to read file: " + e.getMessage())
                    ), null)
            );
        }
    }

    /**
     * Start execution of a validated batch.
     * This is asynchronous - returns immediately and processes in background.
     */
    @PostMapping("/execute")
    public ResponseEntity<ExecuteBatchResponse> execute(
            @PathVariable String profileId,
            @RequestBody ExecuteBatchRequest request,
            Principal principal
    ) {
        String requestedBy = principal != null ? principal.getName() : "system";
        logger.info("Starting batch execution {} for profile {} by {}", request.batchId(), profileId, requestedBy);

        BatchId batchId = BatchId.of(request.batchId());

        Batch batch = payorEnrolmentService.getBatch(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + request.batchId()));

        // Verify batch belongs to this profile
        if (!batch.sourceProfileId().urn().equals(profileId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ExecuteBatchResponse(
                            request.batchId(),
                            "ERROR",
                            0,
                            "Batch does not belong to this profile"
                    ));
        }

        // Start async execution
        payorEnrolmentService.execute(batchId);

        return ResponseEntity.accepted().body(new ExecuteBatchResponse(
                batch.id().toString(),
                "IN_PROGRESS",
                batch.totalItems(),
                "Batch execution started"
        ));
    }

    /**
     * List batches for this profile.
     */
    @GetMapping("/batches")
    public ResponseEntity<List<BatchSummaryDto>> listBatches(@PathVariable String profileId) {
        ProfileId profId = ProfileId.fromUrn(profileId);
        List<Batch> batches = payorEnrolmentService.listBatchesByProfile(profId);

        List<BatchSummaryDto> dtos = batches.stream()
                .map(this::toSummaryDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtos);
    }

    // ========== Helper Methods ==========

    private ValidationResultDto toDto(ValidationResult result) {
        List<ValidationResultDto.ValidationErrorDto> errors = result.errors().stream()
                .map(e -> new ValidationResultDto.ValidationErrorDto(
                        e.payorIndex(),
                        e.businessName(),
                        e.field(),
                        e.message()
                ))
                .collect(Collectors.toList());

        return new ValidationResultDto(
                result.valid(),
                result.payorCount(),
                errors,
                result.batchId()
        );
    }

    private BatchSummaryDto toSummaryDto(Batch batch) {
        return new BatchSummaryDto(
                batch.id().toString(),
                batch.type().name(),
                batch.status().name(),
                batch.status().displayName(),
                batch.totalItems(),
                batch.successCount(),
                batch.failedCount(),
                batch.createdAt()
        );
    }
}
