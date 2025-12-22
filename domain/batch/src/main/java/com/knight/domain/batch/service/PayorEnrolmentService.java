package com.knight.domain.batch.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.knight.domain.batch.aggregate.Batch;
import com.knight.domain.batch.repository.BatchRepository;
import com.knight.domain.batch.types.*;
import com.knight.platform.sharedkernel.BatchId;
import com.knight.platform.sharedkernel.ProfileId;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Application service for payor enrolment batch operations.
 * Orchestrates validation and async execution of payor imports.
 */
@Service
public class PayorEnrolmentService {

    private static final int MAX_PAYORS_PER_FILE = 500;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$"
    );

    private final BatchRepository batchRepository;
    private final PayorEnrolmentProcessor processor;
    private final ObjectMapper objectMapper;

    public PayorEnrolmentService(
            BatchRepository batchRepository,
            PayorEnrolmentProcessor processor,
            ObjectMapper objectMapper) {
        this.batchRepository = batchRepository;
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    /**
     * Phase 1: Validate JSON content and create batch if valid.
     * This is synchronous and returns immediately with validation results.
     */
    @Transactional
    public ValidationResult validate(
            ProfileId sourceProfileId,
            String jsonContent,
            String requestedBy) {

        // 1. Parse JSON
        List<PayorEnrolmentRequest> payors;
        try {
            payors = parseJson(jsonContent);
        } catch (JsonProcessingException e) {
            return ValidationResult.failure(0, List.of(
                    ValidationError.of(0, null, "json", "Invalid JSON format: " + e.getMessage())
            ));
        }

        if (payors.isEmpty()) {
            return ValidationResult.failure(0, List.of(
                    ValidationError.of(0, null, "payors", "No payors found in file")
            ));
        }

        if (payors.size() > MAX_PAYORS_PER_FILE) {
            return ValidationResult.failure(payors.size(), List.of(
                    ValidationError.of(0, null, "payors",
                            "Too many payors. Maximum is " + MAX_PAYORS_PER_FILE)
            ));
        }

        // 2. Validate all payors
        List<ValidationError> errors = new ArrayList<>();
        Set<String> emailsInFile = new HashSet<>();
        Set<String> businessNamesInFile = new HashSet<>();

        for (int i = 0; i < payors.size(); i++) {
            PayorEnrolmentRequest payor = payors.get(i);
            errors.addAll(validatePayor(i, payor, emailsInFile, businessNamesInFile));
        }

        // 3. Check for duplicate business names in database
        for (int i = 0; i < payors.size(); i++) {
            PayorEnrolmentRequest payor = payors.get(i);
            if (payor.businessName() != null && !payor.businessName().isBlank()) {
                if (processor.existsByBusinessName(sourceProfileId, payor.businessName())) {
                    errors.add(ValidationError.of(i, payor.businessName(),
                            "businessName", "Business name already exists"));
                }
            }
        }

        if (!errors.isEmpty()) {
            return ValidationResult.failure(payors.size(), errors);
        }

        // 4. Create batch with items
        Batch batch = Batch.create(BatchType.PAYOR_ENROLMENT, sourceProfileId, requestedBy);

        for (PayorEnrolmentRequest payor : payors) {
            try {
                String inputData = objectMapper.writeValueAsString(payor);
                batch.addItem(inputData);
            } catch (JsonProcessingException e) {
                // Should not happen since we just parsed it
                throw new RuntimeException("Failed to serialize payor data", e);
            }
        }

        batchRepository.save(batch);

        return ValidationResult.success(payors.size(), batch.id().toString());
    }

    /**
     * Phase 2: Execute batch asynchronously.
     * This starts processing in the background and returns immediately.
     */
    @Async
    @Transactional
    public void execute(BatchId batchId) {
        Batch batch = batchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found: " + batchId));

        batch.start();
        batchRepository.save(batch);

        for (Batch.BatchItem item : batch.items()) {
            try {
                item.markInProgress();
                batchRepository.save(batch);

                PayorEnrolmentRequest request = objectMapper.readValue(
                        item.inputData(), PayorEnrolmentRequest.class);

                BatchItemResult result = processor.processPayor(batch.sourceProfileId(), request, batch.createdBy());

                String resultJson = objectMapper.writeValueAsString(result);
                item.markSuccess(resultJson);
                batch.incrementSuccess();

            } catch (Exception e) {
                item.markFailed(e.getMessage());
                batch.incrementFailed();
            }
            batchRepository.save(batch);
        }

        batch.complete();
        batchRepository.save(batch);
    }

    /**
     * Get batch status by ID.
     */
    public Optional<Batch> getBatch(BatchId batchId) {
        return batchRepository.findById(batchId);
    }

    /**
     * List batches for a profile.
     */
    public List<Batch> listBatchesByProfile(ProfileId profileId) {
        return batchRepository.findBySourceProfileId(profileId);
    }

    // ========== Private Methods ==========

    private List<PayorEnrolmentRequest> parseJson(String jsonContent) throws JsonProcessingException {
        // Try parsing as { "payors": [...] } format first
        try {
            var wrapper = objectMapper.readTree(jsonContent);
            if (wrapper.has("payors")) {
                return objectMapper.readValue(
                        wrapper.get("payors").toString(),
                        new TypeReference<List<PayorEnrolmentRequest>>() {}
                );
            }
        } catch (Exception ignored) {
            // Fall through to try array format
        }

        // Try parsing as direct array [...]
        return objectMapper.readValue(
                jsonContent,
                new TypeReference<List<PayorEnrolmentRequest>>() {}
        );
    }

    private List<ValidationError> validatePayor(
            int index,
            PayorEnrolmentRequest payor,
            Set<String> emailsInFile,
            Set<String> businessNamesInFile) {

        List<ValidationError> errors = new ArrayList<>();
        String businessName = payor.businessName();

        // Business name validation
        if (businessName == null || businessName.isBlank()) {
            errors.add(ValidationError.of(index, businessName, "businessName", "Business name is required"));
        } else if (businessName.length() > 255) {
            errors.add(ValidationError.of(index, businessName, "businessName", "Business name exceeds 255 characters"));
        } else if (!businessNamesInFile.add(businessName)) {
            errors.add(ValidationError.of(index, businessName, "businessName", "Duplicate business name in file"));
        }

        // Persons validation
        if (payor.persons() == null || payor.persons().isEmpty()) {
            errors.add(ValidationError.of(index, businessName, "persons", "At least one person is required"));
        } else {
            boolean hasAdmin = false;

            for (int p = 0; p < payor.persons().size(); p++) {
                PayorEnrolmentRequest.PersonRequest person = payor.persons().get(p);
                String fieldPrefix = "persons[" + p + "].";

                // Name validation
                if (person.name() == null || person.name().isBlank()) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "name", "Name is required"));
                } else if (person.name().length() > 100) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "name", "Name exceeds 100 characters"));
                }

                // Email validation
                if (person.email() == null || person.email().isBlank()) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "email", "Email is required"));
                } else if (!EMAIL_PATTERN.matcher(person.email()).matches()) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "email", "Invalid email format"));
                } else if (!emailsInFile.add(person.email().toLowerCase())) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "email", "Duplicate email in file"));
                } else if (processor.existsByEmail(person.email())) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "email", "User with this email already exists"));
                } else if (processor.existsInIdentityProvider(person.email())) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "email", "User already exists in identity provider"));
                }

                // Role validation
                if (person.role() == null || person.role().isBlank()) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "role", "Role is required"));
                } else if (!person.role().equals("ADMIN") && !person.role().equals("CONTACT")) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "role", "Role must be ADMIN or CONTACT"));
                } else if (person.role().equals("ADMIN")) {
                    hasAdmin = true;
                }

                // Phone validation (optional but has max length)
                if (person.phone() != null && person.phone().length() > 50) {
                    errors.add(ValidationError.of(index, businessName, fieldPrefix + "phone", "Phone exceeds 50 characters"));
                }
            }

            if (!hasAdmin) {
                errors.add(ValidationError.of(index, businessName, "persons", "At least one ADMIN person is required"));
            }
        }

        return errors;
    }
}
