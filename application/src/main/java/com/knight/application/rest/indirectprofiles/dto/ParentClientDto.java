package com.knight.application.rest.indirectprofiles.dto;

/**
 * DTO representing a parent client option for indirect profile filtering.
 */
public record ParentClientDto(
    String clientId,
    String displayName
) {}
