package com.knight.application.rest.batch.dto;

import java.util.List;

/**
 * DTO for validation result response.
 */
public record ValidationResultDto(
        boolean valid,
        int payorCount,
        List<ValidationErrorDto> errors,
        String batchId
) {
    public record ValidationErrorDto(
            int payorIndex,
            String businessName,
            String field,
            String message
    ) {}
}
