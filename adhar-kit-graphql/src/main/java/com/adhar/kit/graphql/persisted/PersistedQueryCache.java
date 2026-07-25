package com.adhar.kit.graphql.persisted;

import java.util.Optional;

/**
 * Storage abstraction for Automatic Persisted Queries (APQ).
 *
 * <p>Maps a SHA-256 hash of a GraphQL query document to the query text itself, so that
 * clients can send only the hash on subsequent requests instead of the full query
 * string.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface PersistedQueryCache {

    /**
     * Looks up a previously cached query document by its SHA-256 hash.
     *
     * @param sha256Hash the lower-case hex-encoded SHA-256 hash of the query document
     * @return the cached query document, or empty if not present
     */
    Optional<String> get(String sha256Hash);

    /**
     * Caches a query document under its SHA-256 hash.
     *
     * @param sha256Hash the lower-case hex-encoded SHA-256 hash of the query document
     * @param query      the full GraphQL query document text
     */
    void put(String sha256Hash, String query);

    /**
     * Returns the number of entries currently cached.
     *
     * @return the current cache size
     */
    int size();
}
