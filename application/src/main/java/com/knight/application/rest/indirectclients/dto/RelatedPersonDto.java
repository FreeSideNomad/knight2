package com.knight.application.rest.indirectclients.dto;

import java.time.Instant;

/**
 * REST DTO for related person information.
 */
public record RelatedPersonDto(
    String personId,
    String name,
    String role,
    String email,
    String phone,
    Instant addedAt
) {}
