package com.adhar.kit.dapr.state;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Options for a state write: ETag, time-to-live, concurrency, consistency and store-specific
 * metadata. Immutable; every {@code with*} returns a copy.
 *
 * <pre>{@code
 * dapr.saveState("statestore", "cart:42", cart, SaveOptions.etag(current.getEtag()).withTtl(Duration.ofHours(1)));
 * }</pre>
 *
 * @param etag        the ETag the write is conditional on, or {@code null}
 * @param ttl         time-to-live (sent as {@code ttlInSeconds}, rounded up to a second), or {@code null}
 * @param concurrency concurrency mode, or {@code null} for the store default
 * @param consistency consistency, or {@code null} for the store default
 * @param metadata    store-specific metadata
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record SaveOptions(String etag, Duration ttl, Concurrency concurrency, Consistency consistency,
                          Map<String, String> metadata) {

    private static final SaveOptions NONE = new SaveOptions(null, null, null, null, Map.of());

    public SaveOptions {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** No options: an unconditional write with the store defaults. */
    public static SaveOptions none() {
        return NONE;
    }

    /** A write that only succeeds while the stored ETag still equals {@code etag}. */
    public static SaveOptions etag(String etag) {
        return new SaveOptions(etag, null, Concurrency.FIRST_WRITE, null, Map.of());
    }

    /** A write that expires after {@code ttl}. */
    public static SaveOptions ttl(Duration ttl) {
        return new SaveOptions(null, ttl, null, null, Map.of());
    }

    public SaveOptions withEtag(String newEtag) {
        return new SaveOptions(newEtag, ttl, concurrency, consistency, metadata);
    }

    public SaveOptions withTtl(Duration newTtl) {
        return new SaveOptions(etag, newTtl, concurrency, consistency, metadata);
    }

    public SaveOptions withConcurrency(Concurrency newConcurrency) {
        return new SaveOptions(etag, ttl, newConcurrency, consistency, metadata);
    }

    public SaveOptions withConsistency(Consistency newConsistency) {
        return new SaveOptions(etag, ttl, concurrency, newConsistency, metadata);
    }

    public SaveOptions withMetadata(String key, String value) {
        Map<String, String> copy = new LinkedHashMap<>(metadata);
        copy.put(key, value);
        return new SaveOptions(etag, ttl, concurrency, consistency, copy);
    }

    /**
     * The metadata actually sent: the caller's entries plus {@code ttlInSeconds} when a positive TTL
     * is set (Dapr rejects 0 and sub-second values, so they round up to one second).
     *
     * @return the request metadata
     */
    public Map<String, String> requestMetadata() {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return metadata;
        }
        Map<String, String> copy = new LinkedHashMap<>(metadata);
        long seconds = ttl.toSeconds() + (ttl.toNanosPart() > 0 ? 1 : 0);
        copy.put("ttlInSeconds", String.valueOf(Math.max(1L, seconds)));
        return copy;
    }
}
