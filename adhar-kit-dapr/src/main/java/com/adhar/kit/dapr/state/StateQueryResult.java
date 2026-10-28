package com.adhar.kit.dapr.state;

import java.util.List;
import java.util.Map;

/**
 * One page of a state query (Dapr's alpha query API).
 *
 * @param results  the matching entries
 * @param errors   per-key errors the store reported (key to message)
 * @param token    the continuation token for the next page, or {@code null} on the last page
 * @param metadata response metadata
 * @param <T>      the value type
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record StateQueryResult<T>(List<StateEntry<T>> results, Map<String, String> errors, String token,
                                  Map<String, String> metadata) {

    public StateQueryResult {
        results = results == null ? List.of() : List.copyOf(results);
        errors = errors == null ? Map.of() : Map.copyOf(errors);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** The values of the matching entries, in result order. */
    public List<T> values() {
        return results.stream().map(StateEntry::value).toList();
    }
}
