package com.adhar.kit.dapr.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State with ETag for optimistic concurrency control.
 *
 * @param <T> value type
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StateWithETag<T> {

    /**
     * State value.
     */
    private T value;

    /**
     * ETag for optimistic concurrency.
     */
    private String etag;

    /**
     * Check if state exists.
     *
     * @return true if value is not null
     */
    public boolean exists() {
        return value != null;
    }
}

