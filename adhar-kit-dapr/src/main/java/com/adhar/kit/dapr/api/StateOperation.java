package com.adhar.kit.dapr.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * State operation for transactional state management.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateOperation {

    /**
     * Operation type.
     */
    public enum OperationType {
        UPSERT,
        DELETE
    }

    /**
     * Operation type.
     */
    private OperationType operation;

    /**
     * State key.
     */
    private String key;

    /**
     * State value (for UPSERT).
     */
    private Object value;

    /**
     * ETag for optimistic concurrency.
     */
    private String etag;

    /**
     * Create an upsert operation.
     *
     * @param key state key
     * @param value state value
     * @return state operation
     */
    public static StateOperation upsert(String key, Object value) {
        return StateOperation.builder()
            .operation(OperationType.UPSERT)
            .key(key)
            .value(value)
            .build();
    }

    /**
     * Create an upsert operation with ETag.
     *
     * @param key state key
     * @param value state value
     * @param etag ETag value
     * @return state operation
     */
    public static StateOperation upsert(String key, Object value, String etag) {
        return StateOperation.builder()
            .operation(OperationType.UPSERT)
            .key(key)
            .value(value)
            .etag(etag)
            .build();
    }

    /**
     * Create a delete operation.
     *
     * @param key state key
     * @return state operation
     */
    public static StateOperation delete(String key) {
        return StateOperation.builder()
            .operation(OperationType.DELETE)
            .key(key)
            .build();
    }

    /**
     * Create a delete operation with ETag.
     *
     * @param key state key
     * @param etag ETag value
     * @return state operation
     */
    public static StateOperation delete(String key, String etag) {
        return StateOperation.builder()
            .operation(OperationType.DELETE)
            .key(key)
            .etag(etag)
            .build();
    }
}

