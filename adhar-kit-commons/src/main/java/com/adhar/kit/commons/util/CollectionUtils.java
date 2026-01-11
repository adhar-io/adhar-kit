package com.adhar.kit.commons.util;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class for collection operations.
 *
 * <p>Provides null-safe collection operations and transformations.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * // Null-safe operations
 * boolean empty = CollectionUtils.isEmpty(list);
 * List<String> safe = CollectionUtils.nullSafe(list);
 *
 * // Transformations
 * List<String> names = CollectionUtils.map(users, User::getName);
 * Map<String, User> userMap = CollectionUtils.toMap(users, User::getId);
 *
 * // Filtering
 * List<User> active = CollectionUtils.filter(users, User::isActive);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class CollectionUtils {

    private CollectionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Checks if collection is null or empty.
     *
     * @param collection the collection to check
     * @return true if null or empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Checks if collection is not null and not empty.
     *
     * @param collection the collection to check
     * @return true if not null and not empty
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * Returns an empty list if input is null.
     *
     * @param list the input list
     * @param <T> element type
     * @return non-null list
     */
    public static <T> List<T> nullSafe(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    /**
     * Returns an empty set if input is null.
     *
     * @param set the input set
     * @param <T> element type
     * @return non-null set
     */
    public static <T> Set<T> nullSafe(Set<T> set) {
        return set == null ? Collections.emptySet() : set;
    }

    /**
     * Returns an empty map if input is null.
     *
     * @param map the input map
     * @param <K> key type
     * @param <V> value type
     * @return non-null map
     */
    public static <K, V> Map<K, V> nullSafe(Map<K, V> map) {
        return map == null ? Collections.emptyMap() : map;
    }

    /**
     * Maps a collection to another type.
     *
     * @param collection the input collection
     * @param mapper the mapping function
     * @param <T> input type
     * @param <R> output type
     * @return mapped list
     */
    public static <T, R> List<R> map(Collection<T> collection, Function<T, R> mapper) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        return collection.stream()
            .map(mapper)
            .collect(Collectors.toList());
    }

    /**
     * Filters a collection.
     *
     * @param collection the input collection
     * @param predicate the filter predicate
     * @param <T> element type
     * @return filtered list
     */
    public static <T> List<T> filter(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        return collection.stream()
            .filter(predicate)
            .collect(Collectors.toList());
    }

    /**
     * Converts collection to map.
     *
     * @param collection the input collection
     * @param keyMapper function to extract key
     * @param <T> element type
     * @param <K> key type
     * @return map with elements
     */
    public static <T, K> Map<K, T> toMap(Collection<T> collection, Function<T, K> keyMapper) {
        if (isEmpty(collection)) {
            return Collections.emptyMap();
        }
        return collection.stream()
            .collect(Collectors.toMap(keyMapper, Function.identity()));
    }

    /**
     * Converts collection to map with custom value.
     *
     * @param collection the input collection
     * @param keyMapper function to extract key
     * @param valueMapper function to extract value
     * @param <T> element type
     * @param <K> key type
     * @param <V> value type
     * @return map with key-value pairs
     */
    public static <T, K, V> Map<K, V> toMap(Collection<T> collection,
                                            Function<T, K> keyMapper,
                                            Function<T, V> valueMapper) {
        if (isEmpty(collection)) {
            return Collections.emptyMap();
        }
        return collection.stream()
            .collect(Collectors.toMap(keyMapper, valueMapper));
    }

    /**
     * Partitions collection by predicate.
     *
     * @param collection the input collection
     * @param predicate the partition predicate
     * @param <T> element type
     * @return map with true/false keys
     */
    public static <T> Map<Boolean, List<T>> partition(Collection<T> collection,
                                                       Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return Map.of(true, Collections.emptyList(), false, Collections.emptyList());
        }
        return collection.stream()
            .collect(Collectors.partitioningBy(predicate));
    }

    /**
     * Groups collection by classifier.
     *
     * @param collection the input collection
     * @param classifier the grouping function
     * @param <T> element type
     * @param <K> key type
     * @return grouped map
     */
    public static <T, K> Map<K, List<T>> groupBy(Collection<T> collection,
                                                  Function<T, K> classifier) {
        if (isEmpty(collection)) {
            return Collections.emptyMap();
        }
        return collection.stream()
            .collect(Collectors.groupingBy(classifier));
    }

    /**
     * Finds first element matching predicate.
     *
     * @param collection the input collection
     * @param predicate the search predicate
     * @param <T> element type
     * @return optional containing found element
     */
    public static <T> Optional<T> findFirst(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return Optional.empty();
        }
        return collection.stream()
            .filter(predicate)
            .findFirst();
    }

    /**
     * Checks if any element matches predicate.
     *
     * @param collection the input collection
     * @param predicate the test predicate
     * @param <T> element type
     * @return true if any match
     */
    public static <T> boolean anyMatch(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return false;
        }
        return collection.stream()
            .anyMatch(predicate);
    }

    /**
     * Checks if all elements match predicate.
     *
     * @param collection the input collection
     * @param predicate the test predicate
     * @param <T> element type
     * @return true if all match
     */
    public static <T> boolean allMatch(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return true;
        }
        return collection.stream()
            .allMatch(predicate);
    }

    /**
     * Gets first element or default.
     *
     * @param list the input list
     * @param defaultValue default value if empty
     * @param <T> element type
     * @return first element or default
     */
    public static <T> T firstOrDefault(List<T> list, T defaultValue) {
        return isEmpty(list) ? defaultValue : list.get(0);
    }

    /**
     * Gets last element or default.
     *
     * @param list the input list
     * @param defaultValue default value if empty
     * @param <T> element type
     * @return last element or default
     */
    public static <T> T lastOrDefault(List<T> list, T defaultValue) {
        return isEmpty(list) ? defaultValue : list.get(list.size() - 1);
    }

    /**
     * Creates a list from varargs.
     *
     * @param elements the elements
     * @param <T> element type
     * @return list containing elements
     */
    @SafeVarargs
    public static <T> List<T> listOf(T... elements) {
        return elements == null ? Collections.emptyList() : Arrays.asList(elements);
    }

    /**
     * Creates a set from varargs.
     *
     * @param elements the elements
     * @param <T> element type
     * @return set containing elements
     */
    @SafeVarargs
    public static <T> Set<T> setOf(T... elements) {
        return elements == null ? Collections.emptySet() : new HashSet<>(Arrays.asList(elements));
    }
}

