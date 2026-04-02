package com.adhar.kit.graphql.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for Relay-style cursor-based pagination in GraphQL.
 *
 * <p>Provides records and helper methods to create paginated responses
 * following the <a href="https://relay.dev/graphql/connections.htm">Relay Connection specification</a>.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * List<User> allUsers = userRepository.findAll();
 * Connection<User> connection = PaginationUtils.fromList(allUsers, first, afterCursor);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class PaginationUtils {

    private static final String CURSOR_PREFIX = "cursor:";

    private PaginationUtils() {
        // Utility class
    }

    /**
     * Represents a Relay-style connection containing edges and pagination info.
     *
     * @param edges    the list of edges in this page
     * @param pageInfo metadata about the current page and available pages
     * @param <T>      the node type
     */
    public record Connection<T>(List<Edge<T>> edges, PageInfo pageInfo) {

        /**
         * Returns an empty connection with no edges.
         *
         * @param <T> the node type
         * @return an empty connection
         */
        public static <T> Connection<T> empty() {
            return new Connection<>(Collections.emptyList(),
                    new PageInfo(false, false, null, null));
        }
    }

    /**
     * Represents a single edge in a Relay connection, wrapping a node with its cursor.
     *
     * @param node   the data item
     * @param cursor the opaque cursor for this edge
     * @param <T>    the node type
     */
    public record Edge<T>(T node, String cursor) {
    }

    /**
     * Pagination metadata for a Relay-style connection.
     *
     * @param hasNextPage     true if more items exist after the last edge
     * @param hasPreviousPage true if items exist before the first edge
     * @param startCursor     the cursor of the first edge, or null if empty
     * @param endCursor       the cursor of the last edge, or null if empty
     */
    public record PageInfo(boolean hasNextPage, boolean hasPreviousPage,
                           String startCursor, String endCursor) {
    }

    /**
     * Creates a {@link Connection} from a list of items using cursor-based pagination.
     *
     * <p>The cursor encodes the offset of the item in the original list. Pagination
     * starts after the item identified by {@code afterCursor}, returning at most
     * {@code first} items.</p>
     *
     * @param items       the full list of items to paginate
     * @param first       the maximum number of items to return
     * @param afterCursor the cursor after which to start (null to start from the beginning)
     * @param <T>         the item type
     * @return a connection containing the requested page of items
     * @throws IllegalArgumentException if first is negative
     */
    public static <T> Connection<T> fromList(List<T> items, int first, String afterCursor) {
        if (items == null || items.isEmpty()) {
            return Connection.empty();
        }
        if (first < 0) {
            throw new IllegalArgumentException("'first' must not be negative, got: " + first);
        }

        int startIndex = 0;
        if (afterCursor != null && !afterCursor.isBlank()) {
            startIndex = decodeCursor(afterCursor) + 1;
        }

        if (startIndex >= items.size()) {
            return Connection.empty();
        }

        int endIndex = Math.min(startIndex + first, items.size());
        List<T> slice = items.subList(startIndex, endIndex);

        List<Edge<T>> edges = new java.util.ArrayList<>(slice.size());
        for (int i = 0; i < slice.size(); i++) {
            int absoluteIndex = startIndex + i;
            edges.add(new Edge<>(slice.get(i), encodeCursor(absoluteIndex)));
        }

        boolean hasNextPage = endIndex < items.size();
        boolean hasPreviousPage = startIndex > 0;
        String startCursorValue = edges.isEmpty() ? null : edges.getFirst().cursor();
        String endCursorValue = edges.isEmpty() ? null : edges.getLast().cursor();

        return new Connection<>(
                Collections.unmodifiableList(edges),
                new PageInfo(hasNextPage, hasPreviousPage, startCursorValue, endCursorValue)
        );
    }

    /**
     * Encodes an integer offset into an opaque Base64 cursor string.
     *
     * @param offset the zero-based offset to encode
     * @return the Base64-encoded cursor string
     */
    public static String encodeCursor(int offset) {
        String raw = CURSOR_PREFIX + offset;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque Base64 cursor string back to an integer offset.
     *
     * @param cursor the Base64-encoded cursor string
     * @return the decoded zero-based offset
     * @throws IllegalArgumentException if the cursor is invalid or malformed
     */
    public static int decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            throw new IllegalArgumentException("Cursor must not be null or blank");
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!decoded.startsWith(CURSOR_PREFIX)) {
                throw new IllegalArgumentException("Invalid cursor format: " + cursor);
            }
            return Integer.parseInt(decoded.substring(CURSOR_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Cannot decode cursor: " + cursor, e);
        }
    }
}
