package com.adhar.kit.commons.util;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionUtilsTest {

    @Test
    void isEmpty_shouldReturnTrueForNull() {
        assertThat(CollectionUtils.isEmpty((Collection<?>) null)).isTrue();
    }

    @Test
    void isEmpty_shouldReturnTrueForEmptyCollection() {
        assertThat(CollectionUtils.isEmpty(List.of())).isTrue();
    }

    @Test
    void isEmpty_shouldReturnFalseForNonEmptyCollection() {
        assertThat(CollectionUtils.isEmpty(List.of("a"))).isFalse();
    }

    @Test
    void isNotEmpty_shouldReturnTrueForNonEmptyCollection() {
        assertThat(CollectionUtils.isNotEmpty(List.of("a"))).isTrue();
    }

    @Test
    void isNotEmpty_shouldReturnFalseForNull() {
        assertThat(CollectionUtils.isNotEmpty(null)).isFalse();
    }

    @Test
    void nullSafe_list_shouldReturnEmptyListForNull() {
        assertThat(CollectionUtils.nullSafe((List<String>) null)).isEmpty();
    }

    @Test
    void nullSafe_list_shouldReturnSameListWhenNotNull() {
        List<String> list = List.of("a", "b");
        assertThat(CollectionUtils.nullSafe(list)).isSameAs(list);
    }

    @Test
    void nullSafe_set_shouldReturnEmptySetForNull() {
        assertThat(CollectionUtils.nullSafe((Set<String>) null)).isEmpty();
    }

    @Test
    void nullSafe_map_shouldReturnEmptyMapForNull() {
        assertThat(CollectionUtils.nullSafe((Map<String, String>) null)).isEmpty();
    }

    @Test
    void map_shouldTransformElements() {
        List<String> input = List.of("hello", "world");
        List<Integer> result = CollectionUtils.map(input, String::length);
        assertThat(result).containsExactly(5, 5);
    }

    @Test
    void map_shouldReturnEmptyListForNull() {
        assertThat(CollectionUtils.map(null, Object::toString)).isEmpty();
    }

    @Test
    void filter_shouldFilterElements() {
        List<Integer> input = List.of(1, 2, 3, 4, 5);
        List<Integer> result = CollectionUtils.filter(input, n -> n > 3);
        assertThat(result).containsExactly(4, 5);
    }

    @Test
    void filter_shouldReturnEmptyForNull() {
        assertThat(CollectionUtils.filter(null, x -> true)).isEmpty();
    }

    @Test
    void toMap_shouldCreateMapFromCollection() {
        List<String> input = List.of("apple", "banana");
        Map<Integer, String> result = CollectionUtils.toMap(input, String::length);
        assertThat(result).containsEntry(5, "apple").containsEntry(6, "banana");
    }

    @Test
    void toMap_withValueMapper_shouldCreateMapWithCustomValues() {
        List<String> input = List.of("apple", "banana");
        Map<String, Integer> result = CollectionUtils.toMap(input, s -> s, String::length);
        assertThat(result).containsEntry("apple", 5).containsEntry("banana", 6);
    }

    @Test
    void toMap_shouldReturnEmptyForNull() {
        assertThat(CollectionUtils.toMap(null, Object::toString)).isEmpty();
    }

    @Test
    void partition_shouldSplitByPredicate() {
        List<Integer> input = List.of(1, 2, 3, 4, 5);
        Map<Boolean, List<Integer>> result = CollectionUtils.partition(input, n -> n % 2 == 0);
        assertThat(result.get(true)).containsExactly(2, 4);
        assertThat(result.get(false)).containsExactly(1, 3, 5);
    }

    @Test
    void partition_shouldReturnEmptyListsForNull() {
        Map<Boolean, List<Integer>> result = CollectionUtils.partition(null, x -> true);
        assertThat(result.get(true)).isEmpty();
        assertThat(result.get(false)).isEmpty();
    }

    @Test
    void groupBy_shouldGroupElements() {
        List<String> input = List.of("aa", "bb", "ccc");
        Map<Integer, List<String>> result = CollectionUtils.groupBy(input, String::length);
        assertThat(result.get(2)).containsExactly("aa", "bb");
        assertThat(result.get(3)).containsExactly("ccc");
    }

    @Test
    void groupBy_shouldReturnEmptyForNull() {
        assertThat(CollectionUtils.groupBy(null, Object::toString)).isEmpty();
    }

    @Test
    void findFirst_shouldReturnMatchingElement() {
        List<Integer> input = List.of(1, 2, 3);
        assertThat(CollectionUtils.findFirst(input, n -> n > 1)).contains(2);
    }

    @Test
    void findFirst_shouldReturnEmptyWhenNoMatch() {
        List<Integer> input = List.of(1, 2, 3);
        assertThat(CollectionUtils.findFirst(input, n -> n > 10)).isEmpty();
    }

    @Test
    void findFirst_shouldReturnEmptyForNull() {
        assertThat(CollectionUtils.findFirst(null, x -> true)).isEmpty();
    }

    @Test
    void anyMatch_shouldReturnTrueIfAnyElementMatches() {
        assertThat(CollectionUtils.anyMatch(List.of(1, 2, 3), n -> n == 2)).isTrue();
    }

    @Test
    void anyMatch_shouldReturnFalseIfNoneMatch() {
        assertThat(CollectionUtils.anyMatch(List.of(1, 2, 3), n -> n == 5)).isFalse();
    }

    @Test
    void anyMatch_shouldReturnFalseForNull() {
        assertThat(CollectionUtils.anyMatch(null, x -> true)).isFalse();
    }

    @Test
    void allMatch_shouldReturnTrueIfAllMatch() {
        assertThat(CollectionUtils.allMatch(List.of(2, 4, 6), n -> n % 2 == 0)).isTrue();
    }

    @Test
    void allMatch_shouldReturnFalseIfNotAllMatch() {
        assertThat(CollectionUtils.allMatch(List.of(2, 3, 6), n -> n % 2 == 0)).isFalse();
    }

    @Test
    void allMatch_shouldReturnTrueForNull() {
        assertThat(CollectionUtils.allMatch(null, x -> true)).isTrue();
    }

    @Test
    void firstOrDefault_shouldReturnFirstElement() {
        assertThat(CollectionUtils.firstOrDefault(List.of("a", "b"), "x")).isEqualTo("a");
    }

    @Test
    void firstOrDefault_shouldReturnDefaultForEmpty() {
        assertThat(CollectionUtils.firstOrDefault(List.of(), "x")).isEqualTo("x");
        assertThat(CollectionUtils.firstOrDefault(null, "x")).isEqualTo("x");
    }

    @Test
    void lastOrDefault_shouldReturnLastElement() {
        assertThat(CollectionUtils.lastOrDefault(List.of("a", "b", "c"), "x")).isEqualTo("c");
    }

    @Test
    void lastOrDefault_shouldReturnDefaultForEmpty() {
        assertThat(CollectionUtils.lastOrDefault(List.of(), "x")).isEqualTo("x");
    }

    @Test
    void listOf_shouldCreateListFromVarargs() {
        assertThat(CollectionUtils.listOf("a", "b", "c")).containsExactly("a", "b", "c");
    }

    @Test
    void listOf_shouldReturnEmptyForNull() {
        assertThat(CollectionUtils.listOf((Object[]) null)).isEmpty();
    }

    @Test
    void setOf_shouldCreateSetFromVarargs() {
        assertThat(CollectionUtils.setOf("a", "b", "c")).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void setOf_shouldReturnEmptyForNull() {
        assertThat(CollectionUtils.setOf((Object[]) null)).isEmpty();
    }

    @Test
    void constructor_shouldThrowUnsupportedOperationException() throws Exception {
        var constructor = CollectionUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertThatThrownBy(constructor::newInstance)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
