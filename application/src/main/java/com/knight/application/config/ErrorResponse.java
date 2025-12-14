package com.knight.application.config;

import java.time.Instant;

/**
 * Standard error response DTO
 *
 * Provides consistent error response format across all REST endpoints.
 *
 * @param code Error code (e.g., "NOT_FOUND", "BAD_REQUEST")
 * @param message Human-readable error message
 * @param timestamp When the error occurred
 */
public record ErrorResponse(
    String code,
    String message,
    Instant timestamp
) {
    /**
     * Convenience constructor that sets timestamp to now
     */
    public ErrorResponse(String code, String message) {
        this(code, message, Instant.now());
    }
}
