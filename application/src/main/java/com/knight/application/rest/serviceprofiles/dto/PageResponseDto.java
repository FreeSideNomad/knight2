package com.knight.application.rest.serviceprofiles.dto;

import java.util.List;

/**
 * Generic DTO for paginated responses.
 */
public record PageResponseDto<T>(
    List<T> content,
    long totalElements,
    int page,
    int size,
    int totalPages
) {}
