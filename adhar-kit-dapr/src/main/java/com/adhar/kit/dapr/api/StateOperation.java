package com.adhar.kit.dapr.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.adhar.kit.dapr.state.Concurrency;

import java.util.Map;

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
     * Per-operation metadata (for example {@code ttlInSeconds}, or {@code outbox.projection}).
     */
    private Map<String, String> metadata;

    /**
     * Concurrency of this operation, or {@code null} for the store default. {@code FIRST_WRITE}
     * without an ETag makes an upsert an insert that fails when the key exists.
     */
    private Concurrency concurrency;

    /**
     * Creates an operation without metadata (the original four-field shape).
     *
     * @param operation operation type
     * @param key state key
     * @param value state value (for UPSERT)
     * @param etag ETag, or {@code null}
     */
    public StateOperation(OperationType operation, String key, Object value, String etag) {
        this(operation, key, value, etag, null, null);
    }

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
     * Create an upsert that succeeds only while the stored ETag still equals {@code etag}, or,
     * with a {@code null} ETag, only when the key does not exist yet.
     *
     * @param key state key
     * @param value state value
     * @param etag ETag, or {@code null} for insert-only
     * @return state operation
     */
    public static StateOperation conditionalUpsert(String key, Object value, String etag) {
        return StateOperation.builder()
            .operation(OperationType.UPSERT)
            .key(key)
            .value(value)
            .etag(etag)
            .concurrency(Concurrency.FIRST_WRITE)
            .build();
    }

    /**
     * Create an upsert whose value is what Dapr's transactional outbox publishes in place of the
     * stored value ({@code outbox.projection=true}). Pair it with a plain upsert of the same key in
     * the same transaction: the store keeps the full value, subscribers receive the projection.
     *
     * @param key state key (the same key as the stored upsert)
     * @param projection the event payload to publish
     * @return state operation
     */
    public static StateOperation projection(String key, Object projection) {
        return StateOperation.builder()
            .operation(OperationType.UPSERT)
            .key(key)
            .value(projection)
            .metadata(Map.of("outbox.projection", "true"))
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

