package com.knight.application.rest.policies.dto;

/**
 * Response from authorization check.
 */
public record AuthorizeResponse(
    boolean allowed,
    String reason,
    String effectiveEffect
) {}
