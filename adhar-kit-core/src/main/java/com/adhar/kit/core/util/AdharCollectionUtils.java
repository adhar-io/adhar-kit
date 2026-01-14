package com.adhar.kit.core.util;

import lombok.experimental.UtilityClass;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Collection utility class for common collection operations.
 *
 * <p>Provides null-safe collection operations, transformations, and utilities.</p>
 *
 * <p><b>Usage Examples:</b></p>
 * <pre>{@code
 * // Null-safe checks
 * boolean isEmpty = AdharCollectionUtils.isEmpty(list);
 * boolean isNotEmpty = AdharCollectionUtils.isNotEmpty(list);
 *
 * // Safe operations
 * List<String> safe = AdharCollectionUtils.emptyIfNull(list);
 * int size = AdharCollectionUtils.size(list);
 *
 * // Transformations
 * List<Integer> lengths = AdharCollectionUtils.map(strings, String::length);
 * List<String> filtered = AdharCollectionUtils.filter(strings, s -> s.startsWith("A"));
 *
 * // Partitioning
 * List<List<String>> chunks = AdharCollectionUtils.partition(list, 10);
 *
 * // Set operations
 * Set<String> union = AdharCollectionUtils.union(set1, set2);
 * Set<String> intersection = AdharCollectionUtils.intersection(set1, set2);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@UtilityClass
public class AdharCollectionUtils {

    // ==================== Null-Safe Checks ====================

    /**
     * Checks if a collection is empty or null.
     *
     * @param collection the collection to check
     * @return true if empty or null
     */
    public static boolean isEmpty(Collection<?> collection) {
        return CollectionUtils.isEmpty(collection);
    }

    /**
     * Checks if a collection is not empty.
     *
     * @param collection the collection to check
     * @return true if not empty and not null
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return CollectionUtils.isNotEmpty(collection);
    }

    /**
     * Checks if a map is empty or null.
     *
     * @param map the map to check
     * @return true if empty or null
     */
    public static boolean isEmpty(Map<?, ?> map) {
        return map == null || map.isEmpty();
    }

    /**
     * Checks if a map is not empty.
     *
     * @param map the map to check
     * @return true if not empty and not null
     */
    public static boolean isNotEmpty(Map<?, ?> map) {
        return map != null && !map.isEmpty();
    }

    // ==================== Safe Operations ====================

    /**
     * Returns an empty list if the input is null.
     *
     * @param list the list
     * @param <T> the element type
     * @return the list or empty list
     */
    public static <T> List<T> emptyIfNull(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }

    /**
     * Returns an empty set if the input is null.
     *
     * @param set the set
     * @param <T> the element type
     * @return the set or empty set
     */
    public static <T> Set<T> emptyIfNull(Set<T> set) {
        return set != null ? set : Collections.emptySet();
    }

    /**
     * Returns an empty map if the input is null.
     *
     * @param map the map
     * @param <K> the key type
     * @param <V> the value type
     * @return the map or empty map
     */
    public static <K, V> Map<K, V> emptyIfNull(Map<K, V> map) {
        return map != null ? map : Collections.emptyMap();
    }

    /**
     * Gets the size of a collection safely.
     *
     * @param collection the collection
     * @return size or 0 if null
     */
    public static int size(Collection<?> collection) {
        return collection != null ? collection.size() : 0;
    }

    /**
     * Gets the size of a map safely.
     *
     * @param map the map
     * @return size or 0 if null
     */
    public static int size(Map<?, ?> map) {
        return map != null ? map.size() : 0;
    }

    // ==================== Transformations ====================

    /**
     * Maps a collection using the given function.
     *
     * @param collection the collection
     * @param mapper the mapping function
     * @param <T> the source type
     * @param <R> the result type
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
     * Filters a collection using the given predicate.
     *
     * @param collection the collection
     * @param predicate the filter predicate
     * @param <T> the element type
     * @return filtered list
     */
    public static <T> List<T> filter(Collection<T> collection,
                                      java.util.function.Predicate<T> predicate) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        return collection.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * Removes null elements from a collection.
     *
     * @param collection the collection
     * @param <T> the element type
     * @return collection without nulls
     */
    public static <T> List<T> removeNulls(Collection<T> collection) {
        return filter(collection, Objects::nonNull);
    }

    /**
     * Removes duplicates from a collection.
     *
     * @param collection the collection
     * @param <T> the element type
     * @return list without duplicates
     */
    public static <T> List<T> removeDuplicates(Collection<T> collection) {
        if (isEmpty(collection)) {
            return Collections.emptyList();
        }
        return new ArrayList<>(new LinkedHashSet<>(collection));
    }

    // ==================== Partitioning ====================

    /**
     * Partitions a collection into chunks of the specified size.
     *
     * @param collection the collection
     * @param chunkSize the chunk size
     * @param <T> the element type
     * @return list of chunks
     */
    public static <T> List<List<T>> partition(Collection<T> collection, int chunkSize) {
        if (isEmpty(collection) || chunkSize <= 0) {
            return Collections.emptyList();
        }

        List<List<T>> partitions = new ArrayList<>();
        List<T> currentPartition = new ArrayList<>();

        for (T element : collection) {
            currentPartition.add(element);
            if (currentPartition.size() == chunkSize) {
                partitions.add(new ArrayList<>(currentPartition));
                currentPartition.clear();
            }
        }

        if (!currentPartition.isEmpty()) {
            partitions.add(currentPartition);
        }

        return partitions;
    }

    // ==================== Set Operations ====================

    /**
     * Returns the union of two sets.
     *
     * @param set1 first set
     * @param set2 second set
     * @param <T> the element type
     * @return union set
     */
    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        Set<T> result = new HashSet<>(emptyIfNull(set1));
        result.addAll(emptyIfNull(set2));
        return result;
    }

    /**
     * Returns the intersection of two sets.
     *
     * @param set1 first set
     * @param set2 second set
     * @param <T> the element type
     * @return intersection set
     */
    public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1) || isEmpty(set2)) {
            return Collections.emptySet();
        }
        Set<T> result = new HashSet<>(set1);
        result.retainAll(set2);
        return result;
    }

    /**
     * Returns the difference of two sets (elements in set1 but not in set2).
     *
     * @param set1 first set
     * @param set2 second set
     * @param <T> the element type
     * @return difference set
     */
    public static <T> Set<T> difference(Set<T> set1, Set<T> set2) {
        if (isEmpty(set1)) {
            return Collections.emptySet();
        }
        Set<T> result = new HashSet<>(set1);
        result.removeAll(emptyIfNull(set2));
        return result;
    }

    // ==================== Aggregation ====================

    /**
     * Joins collection elements into a string.
     *
     * @param collection the collection
     * @param delimiter the delimiter
     * @param <T> the element type
     * @return joined string
     */
    public static <T> String join(Collection<T> collection, String delimiter) {
        if (isEmpty(collection)) {
            return "";
        }
        return collection.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(delimiter));
    }

    /**
     * Groups elements by a key function.
     *
     * @param collection the collection
     * @param keyExtractor the key extraction function
     * @param <K> the key type
     * @param <V> the value type
     * @return map of grouped elements
     */
    public static <K, V> Map<K, List<V>> groupBy(Collection<V> collection,
                                                   Function<V, K> keyExtractor) {
        if (isEmpty(collection)) {
            return Collections.emptyMap();
        }
        return collection.stream()
                .collect(Collectors.groupingBy(keyExtractor));
    }

    /**
     * Creates a map from a collection using key and value extractors.
     *
     * @param collection the collection
     * @param keyExtractor the key extraction function
     * @param valueExtractor the value extraction function
     * @param <K> the key type
     * @param <V> the value type
     * @param <T> the element type
     * @return resulting map
     */
    public static <K, V, T> Map<K, V> toMap(Collection<T> collection,
                                             Function<T, K> keyExtractor,
                                             Function<T, V> valueExtractor) {
        if (isEmpty(collection)) {
            return Collections.emptyMap();
        }
        return collection.stream()
                .collect(Collectors.toMap(keyExtractor, valueExtractor));
    }

    // ==================== List Operations ====================

    /**
     * Gets the first element of a list safely.
     *
     * @param list the list
     * @param <T> the element type
     * @return first element or null
     */
    public static <T> T first(List<T> list) {
        return isEmpty(list) ? null : list.get(0);
    }

    /**
     * Gets the last element of a list safely.
     *
     * @param list the list
     * @param <T> the element type
     * @return last element or null
     */
    public static <T> T last(List<T> list) {
        return isEmpty(list) ? null : list.get(list.size() - 1);
    }

    /**
     * Reverses a list.
     *
     * @param list the list
     * @param <T> the element type
     * @return reversed list
     */
    public static <T> List<T> reverse(List<T> list) {
        if (isEmpty(list)) {
            return Collections.emptyList();
        }
        List<T> reversed = new ArrayList<>(list);
        Collections.reverse(reversed);
        return reversed;
    }

    /**
     * Sorts a list using natural ordering.
     *
     * @param list the list
     * @param <T> the element type (must be Comparable)
     * @return sorted list
     */
    public static <T extends Comparable<T>> List<T> sort(List<T> list) {
        if (isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .sorted()
                .collect(Collectors.toList());
    }

    /**
     * Sorts a list using a comparator.
     *
     * @param list the list
     * @param comparator the comparator
     * @param <T> the element type
     * @return sorted list
     */
    public static <T> List<T> sort(List<T> list, Comparator<T> comparator) {
        if (isEmpty(list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    // ==================== Null-Safe Additions ====================

    /**
     * Adds an element to a list if it's not null.
     *
     * @param list the list
     * @param element the element
     * @param <T> the element type
     * @return true if added
     */
    public static <T> boolean addIfNotNull(Collection<T> list, T element) {
        return element != null && list != null && list.add(element);
    }

    /**
     * Creates a list with non-null elements.
     *
     * @param elements the elements
     * @param <T> the element type
     * @return list without nulls
     */
    @SafeVarargs
    public static <T> List<T> listOfNonNull(T... elements) {
        if (elements == null || elements.length == 0) {
            return Collections.emptyList();
        }
        return Arrays.stream(elements)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}

