package com.adhar.kit.metrics.util;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bounds the number of distinct values recorded per tag key to protect the
 * metrics backend from tag cardinality explosions.
 * <p>
 * Each tag key tracks the set of values it has already admitted. Once the
 * configured maximum is reached, every previously unseen value is replaced
 * with the {@link #OVERFLOW_VALUE} placeholder while already-admitted values
 * continue to pass through unchanged.
 * </p>
 *
 * <p>Usage example:</p>
 * <pre>
 * {@code
 * TagCardinalityLimiter limiter = new TagCardinalityLimiter(100);
 * String uriTag = limiter.limit("uri", requestUri); // "other" once 100 distinct uris were seen
 * }
 * </pre>
 */
public class TagCardinalityLimiter {

    /**
     * Replacement value used once a tag key exceeds the allowed cardinality.
     */
    public static final String OVERFLOW_VALUE = "other";

    /**
     * Default maximum number of distinct values allowed per tag key.
     */
    public static final int DEFAULT_MAX_CARDINALITY = 100;

    private final int maxCardinality;
    private final Map<String, Set<String>> seenValuesByKey = new ConcurrentHashMap<>();

    /**
     * Creates a limiter with the default maximum cardinality of {@value #DEFAULT_MAX_CARDINALITY}.
     */
    public TagCardinalityLimiter() {
        this(DEFAULT_MAX_CARDINALITY);
    }

    /**
     * Creates a limiter with the given maximum cardinality per tag key.
     *
     * @param maxCardinality maximum distinct values admitted per tag key (must be positive)
     */
    public TagCardinalityLimiter(int maxCardinality) {
        if (maxCardinality <= 0) {
            throw new IllegalArgumentException("maxCardinality must be positive, got " + maxCardinality);
        }
        this.maxCardinality = maxCardinality;
    }

    /**
     * Returns the given value if it is within the allowed cardinality for the tag key,
     * otherwise returns {@link #OVERFLOW_VALUE}.
     *
     * @param tagKey the tag key (e.g. "uri")
     * @param value  the candidate tag value
     * @return the admitted value or {@link #OVERFLOW_VALUE}
     */
    public String limit(String tagKey, String value) {
        if (value == null || value.isEmpty()) {
            return OVERFLOW_VALUE;
        }

        Set<String> seen = seenValuesByKey.computeIfAbsent(tagKey, key -> ConcurrentHashMap.newKeySet());
        if (seen.contains(value)) {
            return value;
        }
        synchronized (seen) {
            if (seen.contains(value)) {
                return value;
            }
            if (seen.size() >= maxCardinality) {
                return OVERFLOW_VALUE;
            }
            seen.add(value);
            return value;
        }
    }

    /**
     * Returns the number of distinct values currently admitted for a tag key.
     *
     * @param tagKey the tag key
     * @return distinct admitted value count (0 if the key was never used)
     */
    public int cardinality(String tagKey) {
        Set<String> seen = seenValuesByKey.get(tagKey);
        return seen == null ? 0 : seen.size();
    }

    /**
     * Returns the configured maximum cardinality per tag key.
     *
     * @return the maximum cardinality
     */
    public int getMaxCardinality() {
        return maxCardinality;
    }

    /**
     * Clears all admitted values for all tag keys.
     */
    public void reset() {
        seenValuesByKey.clear();
    }
}
