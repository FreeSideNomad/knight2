package com.knight.domain.clients.api;

import java.util.List;

/**
 * Generic page result for paginated queries.
 *
 * @param <T> the type of content in the page
 * @param content the list of items in this page
 * @param page the current page number (0-based)
 * @param size the page size
 * @param totalElements the total number of elements across all pages
 * @param totalPages the total number of pages
 */
public record PageResult<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    /**
     * Creates a PageResult from content and pagination info.
     */
    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageResult<>(content, page, size, totalElements, totalPages);
    }

    /**
     * Returns true if this is the first page.
     */
    public boolean isFirst() {
        return page == 0;
    }

    /**
     * Returns true if this is the last page.
     */
    public boolean isLast() {
        return page >= totalPages - 1;
    }

    /**
     * Returns true if there is a next page.
     */
    public boolean hasNext() {
        return page < totalPages - 1;
    }

    /**
     * Returns true if there is a previous page.
     */
    public boolean hasPrevious() {
        return page > 0;
    }
}
