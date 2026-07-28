package com.adhar.kit.graphql.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ClientRateLimiter}.
 */
class ClientRateLimiterTest {

    @Test
    @DisplayName("tracks independent buckets per client")
    void independentBucketsPerClient() {
        ClientRateLimiter limiter = new ClientRateLimiter(5, 1.0, 100);

        assertThat(limiter.tryAcquire("a", 5)).isTrue();
        assertThat(limiter.tryAcquire("a", 1)).isFalse();
        // different client has its own full bucket
        assertThat(limiter.tryAcquire("b", 5)).isTrue();
        assertThat(limiter.clientCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("evicts least-recently-used bucket when maxClients exceeded")
    void evictsLeastRecentlyUsed() {
        ClientRateLimiter limiter = new ClientRateLimiter(5, 1.0, 2);
        limiter.tryAcquire("a", 1);
        limiter.tryAcquire("b", 1);
        limiter.tryAcquire("c", 1); // should evict "a"
        assertThat(limiter.clientCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("clear removes all buckets")
    void clearRemovesBuckets() {
        ClientRateLimiter limiter = new ClientRateLimiter(5, 1.0, 100);
        limiter.tryAcquire("a", 1);
        limiter.tryAcquire("b", 1);
        assertThat(limiter.clientCount()).isEqualTo(2);
        limiter.clear();
        assertThat(limiter.clientCount()).isZero();
    }

    @Test
    @DisplayName("rejects non-positive maxClients")
    void rejectsInvalidMaxClients() {
        assertThatThrownBy(() -> new ClientRateLimiter(5, 1.0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
