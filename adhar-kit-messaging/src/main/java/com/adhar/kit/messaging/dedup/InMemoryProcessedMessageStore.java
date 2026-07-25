package com.adhar.kit.messaging.dedup;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, TTL-based {@link ProcessedMessageStore}.
 * <p>
 * Suitable for a single instance / development use; it does not share state across
 * application instances, so a message redelivered to a different instance of a
 * horizontally-scaled consumer group would not be recognized as a duplicate. For
 * cross-instance deduplication, back this interface with a shared store (e.g. Redis)
 * instead.
 * <p>
 * Entries older than the configured TTL are purged lazily, on subsequent calls, rather
 * than via a background thread - this keeps the class dependency-free and trivially
 * testable with an injected {@link Clock}.
 */
public class InMemoryProcessedMessageStore implements ProcessedMessageStore {

    private final Duration ttl;
    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();

    public InMemoryProcessedMessageStore(Duration ttl) {
        this(ttl, Clock.systemUTC());
    }

    public InMemoryProcessedMessageStore(Duration ttl, Clock clock) {
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public boolean markIfNotProcessed(String messageId) {
        if (messageId == null || messageId.isBlank()) {
            // Without a usable key we cannot deduplicate; treat as "not a duplicate"
            // rather than silently dropping (or falsely acknowledging) the message.
            return true;
        }
        Instant now = Instant.now(clock);
        purgeExpired(now);
        return seen.putIfAbsent(messageId, now) == null;
    }

    /**
     * Number of message ids currently remembered (for tests/observability).
     *
     * @return the current store size
     */
    public int size() {
        return seen.size();
    }

    private void purgeExpired(Instant now) {
        seen.entrySet().removeIf(entry -> Duration.between(entry.getValue(), now).compareTo(ttl) > 0);
    }
}
