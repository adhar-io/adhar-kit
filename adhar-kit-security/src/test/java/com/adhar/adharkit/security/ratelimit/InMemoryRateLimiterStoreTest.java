package com.adhar.adharkit.security.ratelimit;

import com.adhar.kit.security.ratelimit.InMemoryRateLimiterStore;
import com.adhar.kit.security.ratelimit.RateLimiterStore;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link InMemoryRateLimiterStore}.
 */
class InMemoryRateLimiterStoreTest {

    private final InMemoryRateLimiterStore store = new InMemoryRateLimiterStore();

    @Test
    void firstRequestAllowedWithRemaining() {
        RateLimiterStore.Decision d = store.tryAcquire("c1", 5, 60_000);

        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(4);
        assertThat(d.resetTimeMillis()).isGreaterThan(System.currentTimeMillis());
        assertThat(store.getCacheSize()).isEqualTo(1);
    }

    @Test
    void blocksWhenLimitExceeded() {
        store.tryAcquire("c2", 1, 60_000);
        RateLimiterStore.Decision second = store.tryAcquire("c2", 1, 60_000);

        assertThat(second.allowed()).isFalse();
        assertThat(second.remaining()).isZero();
    }

    @Test
    void windowResetsWhenExpired() {
        RateLimiterStore.Decision first = store.tryAcquire("c3", 1, 0);
        RateLimiterStore.Decision second = store.tryAcquire("c3", 1, 0);

        // A zero-length window means every call starts a fresh window.
        assertThat(first.allowed()).isTrue();
        assertThat(second.allowed()).isTrue();
    }

    @Test
    void distinctClientsTrackedSeparately() {
        store.tryAcquire("a", 1, 60_000);
        store.tryAcquire("b", 1, 60_000);

        assertThat(store.getCacheSize()).isEqualTo(2);
    }

    @Test
    void remainingNeverNegative() {
        store.tryAcquire("c4", 1, 60_000);
        store.tryAcquire("c4", 1, 60_000);
        RateLimiterStore.Decision third = store.tryAcquire("c4", 1, 60_000);

        assertThat(third.remaining()).isZero();
    }
}
