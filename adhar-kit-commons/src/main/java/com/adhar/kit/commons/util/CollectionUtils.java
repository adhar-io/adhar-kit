package com.adhar.kit.commons.util;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class for collection operations and manipulations.
 */
public final class CollectionUtils {

    // Private constructor to prevent instantiation
    private CollectionUtils() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    /**
     * Checks if a collection is null or empty.
     *
     * @param collection the collection to check
     * @return true if the collection is null or empty
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Checks if a collection is not null and not empty.
     *
     * @param collection the collection to check
     * @return true if the collection is not null and not empty
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * Returns the size of a collection, handling null collections.
     *
     * @param collection the collection
     * @return the size, or 0 if null
     */
    public static int size(Collection<?> collection) {
        return collection != null ? collection.size() : 0;
    }

    /**
     * Returns a safe copy of a collection, never null.
     *
     * @param collection the collection to copy
     * @param <T> the element type
     * @return a new list containing the elements, or empty list if input is null
     */
    public static <T> List<T> safeList(Collection<T> collection) {
        return collection != null ? new ArrayList<>(collection) : new ArrayList<>();
    }

    /**
     * Returns a safe copy of a set, never null.
     *
     * @param collection the collection to copy
     * @param <T> the element type
     * @return a new set containing the elements, or empty set if input is null
     */
    public static <T> Set<T> safeSet(Collection<T> collection) {
        return collection != null ? new HashSet<>(collection) : new HashSet<>();
    }

    /**
     * Filters a collection based on a predicate.
     *
     * @param collection the collection to filter
     * @param predicate the filter predicate
     * @param <T> the element type
     * @return a new list containing filtered elements
     */
    public static <T> List<T> filter(Collection<T> collection, Predicate<T> predicate) {
        if (isEmpty(collection) || predicate == null) {
            return new ArrayList<>();
        }

        return collection.stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    /**
     * Maps a collection to a new collection using a mapper function.
     *
     * @param collection the collection to map
     * @param mapper the mapping function
     * @param <T> the input element type
     * @param <R> the output element type
     * @return a new list containing mapped elements
     */
    public static <T, R> List<R> map(Collection<T> collection, Function<T, R> mapper) {
        if (isEmpty(collection) || mapper == null) {
            return new ArrayList<>();
        }

        return collection.stream()
                .map(mapper)
                .collect(Collectors.toList());
    }

    /**
     * Partitions a collection into chunks of specified size.
     *
     * @param collection the collection to partition
     * @param size the chunk size
     * @param <T> the element type
     * @return a list of lists, each containing at most 'size' elements
     */
    public static <T> List<List<T>> partition(Collection<T> collection, int size) {
        if (isEmpty(collection) || size <= 0) {
            return new ArrayList<>();
        }

        List<T> list = new ArrayList<>(collection);
        List<List<T>> partitions = new ArrayList<>();

        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }

        return partitions;
    }

    /**
     * Returns the first element of a collection, or null if empty.
     *
     * @param collection the collection
     * @param <T> the element type
     * @return the first element or null
     */
    public static <T> T getFirst(Collection<T> collection) {
        return isEmpty(collection) ? null : collection.iterator().next();
    }

    /**
     * Returns the first element of a collection, or a default value if empty.
     *
     * @param collection the collection
     * @param defaultValue the default value
     * @param <T> the element type
     * @return the first element or default value
     */
    public static <T> T getFirst(Collection<T> collection, T defaultValue) {
        return isEmpty(collection) ? defaultValue : collection.iterator().next();
    }

    /**
     * Returns the last element of a list, or null if empty.
     *
     * @param list the list
     * @param <T> the element type
     * @return the last element or null
     */
    public static <T> T getLast(List<T> list) {
        return isEmpty(list) ? null : list.get(list.size() - 1);
    }

    /**
     * Returns the intersection of two collections.
     *
     * @param collection1 the first collection
     * @param collection2 the second collection
     * @param <T> the element type
     * @return a new set containing common elements
     */
    public static <T> Set<T> intersection(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1) || isEmpty(collection2)) {
            return new HashSet<>();
        }

        Set<T> result = new HashSet<>(collection1);
        result.retainAll(collection2);
        return result;
    }

    /**
     * Returns the union of two collections.
     *
     * @param collection1 the first collection
     * @param collection2 the second collection
     * @param <T> the element type
     * @return a new set containing all unique elements from both collections
     */
    public static <T> Set<T> union(Collection<T> collection1, Collection<T> collection2) {
        Set<T> result = new HashSet<>();
        if (isNotEmpty(collection1)) {
            result.addAll(collection1);
        }
        if (isNotEmpty(collection2)) {
            result.addAll(collection2);
        }
        return result;
    }

    /**
     * Returns the difference between two collections (elements in first but not in second).
     *
     * @param collection1 the first collection
     * @param collection2 the second collection
     * @param <T> the element type
     * @return a new set containing elements only in the first collection
     */
    public static <T> Set<T> difference(Collection<T> collection1, Collection<T> collection2) {
        if (isEmpty(collection1)) {
            return new HashSet<>();
        }

        Set<T> result = new HashSet<>(collection1);
        if (isNotEmpty(collection2)) {
            result.removeAll(collection2);
        }
        return result;
    }

    /**
     * Removes null elements from a collection.
     *
     * @param collection the collection
     * @param <T> the element type
     * @return a new list without null elements
     */
    public static <T> List<T> removeNulls(Collection<T> collection) {
        if (isEmpty(collection)) {
            return new ArrayList<>();
        }

        return collection.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Checks if any element in the collection matches the predicate.
     *
     * @param collection the collection to check
     * @param predicate the predicate
     * @param <T> the element type
     * @return true if any element matches
     */
    public static <T> boolean anyMatch(Collection<T> collection, Predicate<T> predicate) {
        return isNotEmpty(collection) && predicate != null &&
               collection.stream().anyMatch(predicate);
    }

    /**
     * Checks if all elements in the collection match the predicate.
     *
     * @param collection the collection to check
     * @param predicate the predicate
     * @param <T> the element type
     * @return true if all elements match
     */
    public static <T> boolean allMatch(Collection<T> collection, Predicate<T> predicate) {
        return isEmpty(collection) || (predicate != null &&
               collection.stream().allMatch(predicate));
    }
}
