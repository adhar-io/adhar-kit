package com.adhar.adharkit.messaging.dedup;

import com.adhar.kit.messaging.dedup.InMemoryProcessedMessageStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link InMemoryProcessedMessageStore}. A fake {@link Clock} drives TTL
 * expiry deterministically instead of relying on real wall-clock sleeps.
 */
class InMemoryProcessedMessageStoreTest {

    @Test
    void firstMarkOfAnIdReturnsTrue() {
        InMemoryProcessedMessageStore store = new InMemoryProcessedMessageStore(Duration.ofMinutes(1));

        assertTrue(store.markIfNotProcessed("id-1"));
        assertEquals(1, store.size());
    }

    @Test
    void secondMarkOfTheSameIdReturnsFalse() {
        InMemoryProcessedMessageStore store = new InMemoryProcessedMessageStore(Duration.ofMinutes(1));

        assertTrue(store.markIfNotProcessed("id-1"));
        assertFalse(store.markIfNotProcessed("id-1"));
        assertEquals(1, store.size());
    }

    @Test
    void differentIdsAreIndependent() {
        InMemoryProcessedMessageStore store = new InMemoryProcessedMessageStore(Duration.ofMinutes(1));

        assertTrue(store.markIfNotProcessed("id-1"));
        assertTrue(store.markIfNotProcessed("id-2"));
        assertEquals(2, store.size());
    }

    @Test
    void nullOrBlankIdIsNeverTreatedAsDuplicate() {
        InMemoryProcessedMessageStore store = new InMemoryProcessedMessageStore(Duration.ofMinutes(1));

        assertTrue(store.markIfNotProcessed(null));
        assertTrue(store.markIfNotProcessed(""));
        assertTrue(store.markIfNotProcessed("   "));
        assertEquals(0, store.size(), "null/blank ids must never be stored");
    }

    @Test
    void entryExpiresAfterTtlElapses() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock fakeClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };

        InMemoryProcessedMessageStore store = new InMemoryProcessedMessageStore(Duration.ofSeconds(30), fakeClock);

        assertTrue(store.markIfNotProcessed("id-1"));
        assertFalse(store.markIfNotProcessed("id-1"), "still within TTL");

        // Advance the clock past the TTL.
        now.set(now.get().plusSeconds(31));

        assertTrue(store.markIfNotProcessed("id-1"), "entry should have expired and be treated as new");
    }

    @Test
    void purgesExpiredEntriesOnAccessRatherThanGrowingUnbounded() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-01-01T00:00:00Z"));
        Clock fakeClock = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };

        InMemoryProcessedMessageStore store = new InMemoryProcessedMessageStore(Duration.ofSeconds(10), fakeClock);
        store.markIfNotProcessed("old-id");
        assertEquals(1, store.size());

        now.set(now.get().plusSeconds(20));
        store.markIfNotProcessed("new-id");

        assertEquals(1, store.size(), "the expired entry must be purged, leaving only the new one");
    }
}
