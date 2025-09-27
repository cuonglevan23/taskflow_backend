package com.example.taskmanagement_backend.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for pagination settings in search requests
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationRequest {

    /**
     * Page number (0-based)
     */
    @Builder.Default
    private Integer page = 0;

    /**
     * Page size (number of items per page)
     */
    @Builder.Default
    private Integer size = 20;

    /**
     * Maximum allowed page size
     */
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Get validated page number
     */
    public int getPage() {
        return page != null && page >= 0 ? page : 0;
    }

    /**
     * Get validated page size
     */
    public int getSize() {
        if (size == null || size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
