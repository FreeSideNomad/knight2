package com.knight.portal.services.dto;

import lombok.Data;
import java.util.List;

/**
 * Generic page result for paginated API responses.
 */
@Data
public class PageResult<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean hasNext;
    private boolean hasPrevious;
}
