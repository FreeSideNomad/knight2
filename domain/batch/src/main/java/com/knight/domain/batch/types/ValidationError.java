package com.knight.domain.batch.types;

/**
 * Individual validation error during payor enrolment.
 */
public record ValidationError(
        int payorIndex,
        String businessName,
        String field,
        String message
) {
    /**
     * Create a validation error for a specific field.
     */
    public static ValidationError of(int payorIndex, String businessName, String field, String message) {
        return new ValidationError(payorIndex, businessName, field, message);
    }
}
