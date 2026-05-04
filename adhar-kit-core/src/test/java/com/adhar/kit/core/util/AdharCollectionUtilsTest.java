package com.adhar.kit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AdharCollectionUtils Tests")
class AdharCollectionUtilsTest {

    @Nested
    @DisplayName("Null-Safe Checks")
    class NullSafeChecks {

        @Test
        void isEmpty_collection() {
            assertThat(AdharCollectionUtils.isEmpty((Collection<?>) null)).isTrue();
            assertThat(AdharCollectionUtils.isEmpty(List.of())).isTrue();
            assertThat(AdharCollectionUtils.isEmpty(List.of("a"))).isFalse();
        }

        @Test
        void isNotEmpty_collection() {
            assertThat(AdharCollectionUtils.isNotEmpty((Collection<?>) null)).isFalse();
            assertThat(AdharCollectionUtils.isNotEmpty(List.of("a"))).isTrue();
        }

        @Test
        void isEmpty_map() {
            assertThat(AdharCollectionUtils.isEmpty((Map<?, ?>) null)).isTrue();
            assertThat(AdharCollectionUtils.isEmpty(Map.of())).isTrue();
            assertThat(AdharCollectionUtils.isEmpty(Map.of("k", "v"))).isFalse();
        }

        @Test
        void isNotEmpty_map() {
            assertThat(AdharCollectionUtils.isNotEmpty((Map<?, ?>) null)).isFalse();
            assertThat(AdharCollectionUtils.isNotEmpty(Map.of("k", "v"))).isTrue();
        }
    }

    @Nested
    @DisplayName("Safe Operations")
    class SafeOps {

        @Test
        void emptyIfNull_list() {
            assertThat(AdharCollectionUtils.emptyIfNull((List<String>) null)).isEmpty();
            List<String> list = List.of("a");
            assertThat(AdharCollectionUtils.emptyIfNull(list)).isSameAs(list);
        }

        @Test
        void emptyIfNull_set() {
            assertThat(AdharCollectionUtils.emptyIfNull((Set<String>) null)).isEmpty();
        }

        @Test
        void emptyIfNull_map() {
            assertThat(AdharCollectionUtils.emptyIfNull((Map<String, String>) null)).isEmpty();
        }

        @Test
        void size_collection() {
            assertThat(AdharCollectionUtils.size((Collection<?>) null)).isZero();
            assertThat(AdharCollectionUtils.size(List.of("a", "b"))).isEqualTo(2);
        }

        @Test
        void size_map() {
            assertThat(AdharCollectionUtils.size((Map<?, ?>) null)).isZero();
            assertThat(AdharCollectionUtils.size(Map.of("k", "v"))).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Transformations")
    class Transformations {

        @Test
        void map_transformsElements() {
            List<String> result = AdharCollectionUtils.map(List.of("a", "bb", "ccc"), String::length)
                .stream().map(String::valueOf).toList();
            List<Integer> lengths = AdharCollectionUtils.map(List.of("a", "bb"), String::length);
            assertThat(lengths).containsExactly(1, 2);
        }

        @Test
        void map_emptyForNull() {
            assertThat(AdharCollectionUtils.map(null, Object::toString)).isEmpty();
        }

        @Test
        void filter_filtersElements() {
            List<String> result = AdharCollectionUtils.filter(
                List.of("apple", "banana", "avocado"), s -> s.startsWith("a"));
            assertThat(result).containsExactly("apple", "avocado");
        }

        @Test
        void filter_emptyForNull() {
            assertThat(AdharCollectionUtils.filter(null, s -> true)).isEmpty();
        }

        @Test
        void removeNulls_removesNullElements() {
            List<String> input = new ArrayList<>(Arrays.asList("a", null, "b", null));
            assertThat(AdharCollectionUtils.removeNulls(input)).containsExactly("a", "b");
        }

        @Test
        void removeDuplicates_removesDups() {
            assertThat(AdharCollectionUtils.removeDuplicates(List.of("a", "b", "a", "c")))
                .containsExactly("a", "b", "c");
            assertThat(AdharCollectionUtils.removeDuplicates(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Partitioning")
    class Partitioning {

        @Test
        void partition_chunksCorrectly() {
            List<List<Integer>> chunks = AdharCollectionUtils.partition(List.of(1, 2, 3, 4, 5), 2);
            assertThat(chunks).hasSize(3);
            assertThat(chunks.get(0)).containsExactly(1, 2);
            assertThat(chunks.get(1)).containsExactly(3, 4);
            assertThat(chunks.get(2)).containsExactly(5);
        }

        @Test
        void partition_emptyForNullOrInvalidSize() {
            assertThat(AdharCollectionUtils.partition(null, 2)).isEmpty();
            assertThat(AdharCollectionUtils.partition(List.of(1), 0)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Set Operations")
    class SetOps {

        @Test
        void union_combinesSets() {
            Set<String> result = AdharCollectionUtils.union(Set.of("a", "b"), Set.of("b", "c"));
            assertThat(result).containsExactlyInAnyOrder("a", "b", "c");
        }

        @Test
        void intersection_findsCommon() {
            Set<String> result = AdharCollectionUtils.intersection(Set.of("a", "b"), Set.of("b", "c"));
            assertThat(result).containsExactly("b");
        }

        @Test
        void intersection_emptyForNull() {
            assertThat(AdharCollectionUtils.intersection(null, Set.of("a"))).isEmpty();
        }

        @Test
        void difference_findsOnlyInFirst() {
            Set<String> result = AdharCollectionUtils.difference(Set.of("a", "b"), Set.of("b", "c"));
            assertThat(result).containsExactly("a");
            assertThat(AdharCollectionUtils.difference(null, Set.of("a"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("Aggregation")
    class Aggregation {

        @Test
        void join_joinsElements() {
            assertThat(AdharCollectionUtils.join(List.of("a", "b", "c"), ", ")).isEqualTo("a, b, c");
            assertThat(AdharCollectionUtils.join(null, ",")).isEmpty();
        }

        @Test
        void groupBy_groupsElements() {
            Map<Integer, List<String>> grouped = AdharCollectionUtils.groupBy(
                List.of("a", "bb", "c", "dd"), String::length);
            assertThat(grouped.get(1)).containsExactlyInAnyOrder("a", "c");
            assertThat(grouped.get(2)).containsExactlyInAnyOrder("bb", "dd");
        }

        @Test
        void groupBy_emptyForNull() {
            assertThat(AdharCollectionUtils.groupBy(null, Object::toString)).isEmpty();
        }

        @Test
        void toMap_createsMap() {
            Map<Integer, String> map = AdharCollectionUtils.toMap(
                List.of("a", "bb"), String::length, s -> s);
            assertThat(map).containsEntry(1, "a").containsEntry(2, "bb");
            assertThat(AdharCollectionUtils.toMap(null, Object::toString, Object::toString)).isEmpty();
        }
    }

    @Nested
    @DisplayName("List Operations")
    class ListOps {

        @Test
        void first_returnsFirstOrNull() {
            assertThat(AdharCollectionUtils.first(List.of("a", "b"))).isEqualTo("a");
            String firstNull = AdharCollectionUtils.first(null);
            assertThat(firstNull).isNull();
            String firstEmpty = AdharCollectionUtils.first(List.of());
            assertThat(firstEmpty).isNull();
        }

        @Test
        void last_returnsLastOrNull() {
            assertThat(AdharCollectionUtils.last(List.of("a", "b"))).isEqualTo("b");
            String lastNull = AdharCollectionUtils.last(null);
            assertThat(lastNull).isNull();
        }

        @Test
        void reverse_reversesElements() {
            assertThat(AdharCollectionUtils.reverse(List.of(1, 2, 3))).containsExactly(3, 2, 1);
            assertThat(AdharCollectionUtils.reverse(null)).isEmpty();
        }

        @Test
        void sort_sortsNaturally() {
            assertThat(AdharCollectionUtils.sort(List.of(3, 1, 2))).containsExactly(1, 2, 3);
            assertThat(AdharCollectionUtils.sort((List<Integer>) null)).isEmpty();
        }

        @Test
        void sort_withComparator() {
            assertThat(AdharCollectionUtils.sort(List.of("bb", "a", "ccc"),
                Comparator.comparingInt(String::length)))
                .containsExactly("a", "bb", "ccc");
        }

        @Test
        void addIfNotNull_addsNonNull() {
            List<String> list = new ArrayList<>();
            assertThat(AdharCollectionUtils.addIfNotNull(list, "a")).isTrue();
            assertThat(AdharCollectionUtils.addIfNotNull(list, null)).isFalse();
            assertThat(list).containsExactly("a");
        }

        @Test
        void listOfNonNull_filtersNulls() {
            assertThat(AdharCollectionUtils.listOfNonNull("a", null, "b")).containsExactly("a", "b");
            assertThat(AdharCollectionUtils.listOfNonNull((Object[]) null)).isEmpty();
        }
    }
}
