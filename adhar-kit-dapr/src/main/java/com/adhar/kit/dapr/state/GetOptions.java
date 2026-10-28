package com.adhar.kit.dapr.state;

import java.util.Map;

/**
 * Options for a state read.
 *
 * @param consistency consistency, or {@code null} for the store default
 * @param metadata    store-specific metadata
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record GetOptions(Consistency consistency, Map<String, String> metadata) {

    private static final GetOptions NONE = new GetOptions(null, Map.of());

    public GetOptions {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static GetOptions none() {
        return NONE;
    }

    public static GetOptions strong() {
        return new GetOptions(Consistency.STRONG, Map.of());
    }
}
