package com.adhar.kit.commons.idempotency;

import com.adhar.kit.commons.idempotency.IdempotencyStore.Outcome;
import com.adhar.kit.commons.idempotency.IdempotencyStore.Status;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryIdempotencyStoreTest {

    /** A clock whose instant can be advanced manually for deterministic TTL tests. */
    static class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-01-01T00:00:00Z");

        void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

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
            return instant;
        }
    }

    private final MutableClock clock = new MutableClock();
    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(clock);

    @Test
    void firstBegin_shouldAcquire() {
        Outcome outcome = store.begin("k1", 60);
        assertThat(outcome.status()).isEqualTo(Status.ACQUIRED);
        assertThat(outcome.result()).isNull();
    }

    @Test
    void secondBegin_whileInFlight_shouldReportInProgress() {
        store.begin("k1", 60);
        Outcome outcome = store.begin("k1", 60);
        assertThat(outcome.status()).isEqualTo(Status.IN_PROGRESS);
    }

    @Test
    void begin_afterComplete_shouldReturnStoredResult() {
        store.begin("k1", 60);
        store.complete("k1", "result-1", 60);
        Outcome outcome = store.begin("k1", 60);
        assertThat(outcome.status()).isEqualTo(Status.COMPLETED);
        assertThat(outcome.result()).isEqualTo("result-1");
    }

    @Test
    void begin_afterCompleteWithNullResult_shouldReturnCompletedNull() {
        store.begin("k1", 60);
        store.complete("k1", null, 60);
        Outcome outcome = store.begin("k1", 60);
        assertThat(outcome.status()).isEqualTo(Status.COMPLETED);
        assertThat(outcome.result()).isNull();
    }

    @Test
    void begin_afterTtlExpiry_shouldReacquire() {
        store.begin("k1", 60);
        store.complete("k1", "result-1", 60);
        clock.advanceSeconds(61);
        Outcome outcome = store.begin("k1", 60);
        assertThat(outcome.status()).isEqualTo(Status.ACQUIRED);
    }

    @Test
    void inProgressMarker_shouldAlsoExpire() {
        store.begin("k1", 30);
        clock.advanceSeconds(31);
        assertThat(store.begin("k1", 30).status()).isEqualTo(Status.ACQUIRED);
    }

    @Test
    void abort_shouldReleaseKey() {
        store.begin("k1", 60);
        store.abort("k1");
        assertThat(store.begin("k1", 60).status()).isEqualTo(Status.ACQUIRED);
    }

    @Test
    void purgeExpired_shouldRemoveOnlyExpiredEntries() {
        store.begin("expired", 10);
        store.begin("alive", 100);
        clock.advanceSeconds(11);
        assertThat(store.size()).isEqualTo(2);
        store.purgeExpired();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void differentKeys_shouldBeIndependent() {
        store.begin("k1", 60);
        assertThat(store.begin("k2", 60).status()).isEqualTo(Status.ACQUIRED);
    }

    @Test
    void defaultConstructor_shouldUseSystemClock() {
        InMemoryIdempotencyStore systemStore = new InMemoryIdempotencyStore();
        assertThat(systemStore.begin("k1", 60).status()).isEqualTo(Status.ACQUIRED);
        assertThat(systemStore.begin("k1", 60).status()).isEqualTo(Status.IN_PROGRESS);
    }
}
