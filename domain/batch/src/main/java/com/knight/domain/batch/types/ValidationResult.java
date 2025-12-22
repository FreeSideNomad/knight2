package com.knight.domain.batch.types;

import java.util.List;

/**
 * Result of payor enrolment validation.
 */
public record ValidationResult(
        boolean valid,
        int payorCount,
        List<ValidationError> errors,
        String batchId  // Only set if valid=true
) {
    /**
     * Create a successful validation result with a batch ID.
     */
    public static ValidationResult success(int payorCount, String batchId) {
        return new ValidationResult(true, payorCount, List.of(), batchId);
    }

    /**
     * Create a failed validation result with errors.
     */
    public static ValidationResult failure(int payorCount, List<ValidationError> errors) {
        return new ValidationResult(false, payorCount, errors, null);
    }
}
