package com.knight.application.rest.batch.dto;

/**
 * Response after starting batch execution.
 */
public record ExecuteBatchResponse(
        String batchId,
        String status,
        int totalItems,
        String message
) {}
