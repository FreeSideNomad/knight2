package com.knight.application.rest.batch.dto;

/**
 * Request to start batch execution.
 */
public record ExecuteBatchRequest(
        String batchId
) {}
