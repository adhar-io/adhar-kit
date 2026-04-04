package com.adhar.kit.graphql.pagination;

import com.adhar.kit.graphql.pagination.PaginationUtils.Connection;
import com.adhar.kit.graphql.pagination.PaginationUtils.Edge;
import com.adhar.kit.graphql.pagination.PaginationUtils.PageInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link PaginationUtils}.
 */
class PaginationUtilsTest {

    // ----------------------------------------------------------------
    // fromList - empty / null
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("fromList() with empty or null list")
    class FromListEmpty {

        @Test
        @DisplayName("should return empty connection for null list")
        void nullList() {
            Connection<String> result = PaginationUtils.fromList(null, 10, null);

            assertThat(result.edges()).isEmpty();
            assertThat(result.pageInfo().hasNextPage()).isFalse();
            assertThat(result.pageInfo().hasPreviousPage()).isFalse();
            assertThat(result.pageInfo().startCursor()).isNull();
            assertThat(result.pageInfo().endCursor()).isNull();
        }

        @Test
        @DisplayName("should return empty connection for empty list")
        void emptyList() {
            Connection<String> result = PaginationUtils.fromList(List.of(), 10, null);

            assertThat(result.edges()).isEmpty();
            assertThat(result.pageInfo().hasNextPage()).isFalse();
            assertThat(result.pageInfo().hasPreviousPage()).isFalse();
        }
    }

    // ----------------------------------------------------------------
    // fromList - basic pagination
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("fromList() with items")
    class FromListWithItems {

        @Test
        @DisplayName("should return first N items when no cursor is provided")
        void firstTwoNoCursor() {
            List<String> items = List.of("a", "b", "c", "d", "e");

            Connection<String> result = PaginationUtils.fromList(items, 2, null);

            assertThat(result.edges()).hasSize(2);
            assertThat(result.edges().get(0).node()).isEqualTo("a");
            assertThat(result.edges().get(1).node()).isEqualTo("b");
        }

        @Test
        @DisplayName("should set hasNextPage when more items exist")
        void hasNextPage() {
            List<String> items = List.of("a", "b", "c");

            Connection<String> result = PaginationUtils.fromList(items, 2, null);

            assertThat(result.pageInfo().hasNextPage()).isTrue();
        }

        @Test
        @DisplayName("should not set hasNextPage when all items fit")
        void noNextPage() {
            List<String> items = List.of("a", "b");

            Connection<String> result = PaginationUtils.fromList(items, 5, null);

            assertThat(result.pageInfo().hasNextPage()).isFalse();
        }
    }

    // ----------------------------------------------------------------
    // fromList - afterCursor
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("fromList() with afterCursor")
    class FromListWithCursor {

        @Test
        @DisplayName("should start after the cursor position")
        void afterCursor() {
            List<String> items = List.of("a", "b", "c", "d", "e");
            String cursorForB = PaginationUtils.encodeCursor(1);

            Connection<String> result = PaginationUtils.fromList(items, 2, cursorForB);

            assertThat(result.edges()).hasSize(2);
            assertThat(result.edges().get(0).node()).isEqualTo("c");
            assertThat(result.edges().get(1).node()).isEqualTo("d");
            assertThat(result.pageInfo().hasPreviousPage()).isTrue();
            assertThat(result.pageInfo().hasNextPage()).isTrue();
        }

        @Test
        @DisplayName("should return empty connection when cursor is past last item")
        void cursorPastEnd() {
            List<String> items = List.of("a", "b", "c");
            String cursorForLast = PaginationUtils.encodeCursor(2);

            Connection<String> result = PaginationUtils.fromList(items, 10, cursorForLast);

            assertThat(result.edges()).isEmpty();
        }
    }

    // ----------------------------------------------------------------
    // fromList - first > list size
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("fromList() when first > list size")
    class FromListFirstLarger {

        @Test
        @DisplayName("should return all items and no next page")
        void returnsAll() {
            List<String> items = List.of("x", "y");

            Connection<String> result = PaginationUtils.fromList(items, 100, null);

            assertThat(result.edges()).hasSize(2);
            assertThat(result.pageInfo().hasNextPage()).isFalse();
            assertThat(result.pageInfo().hasPreviousPage()).isFalse();
        }
    }

    // ----------------------------------------------------------------
    // encodeCursor / decodeCursor roundtrip
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("encodeCursor / decodeCursor")
    class CursorRoundtrip {

        @Test
        @DisplayName("should roundtrip encode and decode correctly")
        void roundtrip() {
            for (int i = 0; i < 10; i++) {
                String cursor = PaginationUtils.encodeCursor(i);
                int decoded = PaginationUtils.decodeCursor(cursor);
                assertThat(decoded).isEqualTo(i);
            }
        }

        @Test
        @DisplayName("should throw on null cursor")
        void throwsOnNull() {
            assertThatThrownBy(() -> PaginationUtils.decodeCursor(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw on blank cursor")
        void throwsOnBlank() {
            assertThatThrownBy(() -> PaginationUtils.decodeCursor("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw on invalid base64 cursor")
        void throwsOnInvalidCursor() {
            assertThatThrownBy(() -> PaginationUtils.decodeCursor("not-a-valid-cursor!!"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ----------------------------------------------------------------
    // PageInfo
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("PageInfo")
    class PageInfoTests {

        @Test
        @DisplayName("hasPreviousPage should be false at start")
        void noPreviousAtStart() {
            List<String> items = List.of("a", "b", "c");

            Connection<String> result = PaginationUtils.fromList(items, 2, null);

            assertThat(result.pageInfo().hasPreviousPage()).isFalse();
        }

        @Test
        @DisplayName("hasPreviousPage should be true when using afterCursor")
        void previousWhenCursor() {
            List<String> items = List.of("a", "b", "c");
            String cursor = PaginationUtils.encodeCursor(0);

            Connection<String> result = PaginationUtils.fromList(items, 2, cursor);

            assertThat(result.pageInfo().hasPreviousPage()).isTrue();
        }

        @Test
        @DisplayName("startCursor and endCursor should be set for non-empty results")
        void cursorsSet() {
            List<String> items = List.of("a", "b", "c");

            Connection<String> result = PaginationUtils.fromList(items, 2, null);

            assertThat(result.pageInfo().startCursor()).isNotNull();
            assertThat(result.pageInfo().endCursor()).isNotNull();
            assertThat(result.pageInfo().startCursor())
                    .isNotEqualTo(result.pageInfo().endCursor());
        }
    }

    // ----------------------------------------------------------------
    // Connection edges
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Connection edges")
    class ConnectionEdges {

        @Test
        @DisplayName("edges should contain correct nodes in order")
        void correctNodes() {
            List<Integer> items = List.of(10, 20, 30, 40, 50);

            Connection<Integer> result = PaginationUtils.fromList(items, 3, null);

            List<Integer> nodes = result.edges().stream()
                    .map(Edge::node)
                    .toList();

            assertThat(nodes).containsExactly(10, 20, 30);
        }

        @Test
        @DisplayName("each edge should have a unique cursor")
        void uniqueCursors() {
            List<String> items = List.of("a", "b", "c");

            Connection<String> result = PaginationUtils.fromList(items, 3, null);

            List<String> cursors = result.edges().stream()
                    .map(Edge::cursor)
                    .toList();

            assertThat(cursors).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("negative first should throw IllegalArgumentException")
        void negativeFirst() {
            assertThatThrownBy(() -> PaginationUtils.fromList(List.of("a"), -1, null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negative");
        }
    }
}
