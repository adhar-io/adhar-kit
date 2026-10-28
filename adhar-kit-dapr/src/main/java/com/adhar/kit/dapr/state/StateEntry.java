package com.adhar.kit.dapr.state;

/**
 * A stored value with its key and ETag, as returned by bulk reads and queries.
 *
 * @param key   the key
 * @param value the value, or {@code null} when the key does not exist
 * @param etag  the ETag, or {@code null}
 * @param <T>   the value type
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record StateEntry<T>(String key, T value, String etag) {

    /** {@code true} when the key holds a value. */
    public boolean exists() {
        return value != null;
    }
}
