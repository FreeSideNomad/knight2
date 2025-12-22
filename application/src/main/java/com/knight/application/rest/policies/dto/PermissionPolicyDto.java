package com.knight.application.rest.policies.dto;

import java.time.Instant;

/**
 * Permission policy response DTO.
 */
public record PermissionPolicyDto(
    String id,
    String profileId,
    String subject,
    String action,
    String resource,
    String effect,
    String description,
    boolean systemPolicy,
    Instant createdAt,
    String createdBy,
    Instant updatedAt
) {}
