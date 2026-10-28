package com.adhar.kit.dapr.state;

import java.util.Map;

/**
 * Options for a state delete.
 *
 * @param etag        the ETag the delete is conditional on, or {@code null}
 * @param concurrency concurrency mode, or {@code null} for the store default
 * @param consistency consistency, or {@code null} for the store default
 * @param metadata    store-specific metadata
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record DeleteOptions(String etag, Concurrency concurrency, Consistency consistency,
                            Map<String, String> metadata) {

    private static final DeleteOptions NONE = new DeleteOptions(null, null, null, Map.of());

    public DeleteOptions {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static DeleteOptions none() {
        return NONE;
    }

    /** A delete that only succeeds while the stored ETag still equals {@code etag}. */
    public static DeleteOptions etag(String etag) {
        return new DeleteOptions(etag, Concurrency.FIRST_WRITE, null, Map.of());
    }
}
