package com.adhar.kit.docs.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard pageable response model for API documentation.
 *
 * <p>Provides consistent pagination structure across all APIs.</p>
 *
 * <p><b>Example JSON:</b></p>
 * <pre>{@code
 * {
 *   "content": [...],
 *   "pagination": {
 *     "page": 0,
 *     "size": 20,
 *     "totalPages": 5,
 *     "totalElements": 100,
 *     "first": true,
 *     "last": false,
 *     "hasNext": true,
 *     "hasPrevious": false
 *   }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageableResponse<T> {

    /**
     * Page content.
     */
    private java.util.List<T> content;

    /**
     * Pagination metadata.
     */
    private PaginationMetadata pagination;

    /**
     * Pagination metadata.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationMetadata {
        /**
         * Current page number (0-based).
         */
        private int page;

        /**
         * Page size.
         */
        private int size;

        /**
         * Total number of pages.
         */
        private int totalPages;

        /**
         * Total number of elements.
         */
        private long totalElements;

        /**
         * Is first page.
         */
        private boolean first;

        /**
         * Is last page.
         */
        private boolean last;

        /**
         * Has next page.
         */
        private boolean hasNext;

        /**
         * Has previous page.
         */
        private boolean hasPrevious;
    }
}

