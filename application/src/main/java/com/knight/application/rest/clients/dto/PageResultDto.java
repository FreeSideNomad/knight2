package com.knight.application.rest.clients.dto;

import java.util.List;

/**
 * Generic DTO for paginated results.
 *
 * @param <T> the type of content in the page
 */
public record PageResultDto<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    boolean hasNext,
    boolean hasPrevious
) {
    public static <T> PageResultDto<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        boolean first = page == 0;
        boolean last = page >= totalPages - 1;
        boolean hasNext = page < totalPages - 1;
        boolean hasPrevious = page > 0;
        return new PageResultDto<>(content, page, size, totalElements, totalPages, first, last, hasNext, hasPrevious);
    }
}
