package com.adhar.kit.dapr.state;

import java.util.Objects;

/**
 * One value of a bulk save.
 *
 * @param key     the key
 * @param value   the value
 * @param options per-item options (ETag, TTL, ...)
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record StateItem(String key, Object value, SaveOptions options) {

    public StateItem {
        Objects.requireNonNull(key, "key must not be null");
        options = options == null ? SaveOptions.none() : options;
    }

    public static StateItem of(String key, Object value) {
        return new StateItem(key, value, SaveOptions.none());
    }
}
