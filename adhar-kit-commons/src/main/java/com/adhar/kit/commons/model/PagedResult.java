package com.adhar.kit.commons.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic paged result wrapper for paginated API responses.
 * Provides consistent structure for paginated data across the platform.
 *
 * @param <T> the type of the data elements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PagedResult<T> {

    private List<T> content;
    private PageMetadata page;

    /**
     * Metadata about the page information.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PageMetadata {
        private int number;        // Current page number (0-based)
        private int size;          // Page size
        private long totalElements; // Total number of elements
        private int totalPages;    // Total number of pages
        private boolean first;     // Is this the first page?
        private boolean last;      // Is this the last page?
        private boolean hasNext;   // Has next page?
        private boolean hasPrevious; // Has previous page?
        private int numberOfElements; // Number of elements in current page
        private boolean empty;     // Is the page empty?
    }

    /**
     * Creates a paged result from a list of content and page information.
     *
     * @param content the list of content items
     * @param pageNumber current page number (0-based)
     * @param pageSize page size
     * @param totalElements total number of elements
     * @param <T> the type of content items
     * @return PagedResult instance
     */
    public static <T> PagedResult<T> of(List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        int numberOfElements = content != null ? content.size() : 0;

        PageMetadata pageMetadata = PageMetadata.builder()
                .number(pageNumber)
                .size(pageSize)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(pageNumber == 0)
                .last(pageNumber >= totalPages - 1)
                .hasNext(pageNumber < totalPages - 1)
                .hasPrevious(pageNumber > 0)
                .numberOfElements(numberOfElements)
                .empty(numberOfElements == 0)
                .build();

        return PagedResult.<T>builder()
                .content(content)
                .page(pageMetadata)
                .build();
    }

    /**
     * Creates an empty paged result.
     *
     * @param pageNumber current page number
     * @param pageSize page size
     * @param <T> the type of content items
     * @return empty PagedResult instance
     */
    public static <T> PagedResult<T> empty(int pageNumber, int pageSize) {
        return of(List.of(), pageNumber, pageSize, 0L);
    }

    /**
     * Creates a single page result (non-paginated).
     *
     * @param content the content list
     * @param <T> the type of content items
     * @return PagedResult with single page
     */
    public static <T> PagedResult<T> singlePage(List<T> content) {
        return of(content, 0, content.size(), content.size());
    }
}
