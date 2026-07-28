package com.adhar.kit.graphql.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link TokenBucket}.
 */
class TokenBucketTest {

    @Test
    @DisplayName("consumes tokens while capacity remains and rejects when exhausted")
    void consumesAndRejects() {
        long t0 = 0L;
        TokenBucket bucket = new TokenBucket(10, 1.0, t0);

        assertThat(bucket.tryConsume(6, t0)).isTrue();
        assertThat(bucket.tryConsume(4, t0)).isTrue();
        // bucket now empty, and no time has passed
        assertThat(bucket.tryConsume(1, t0)).isFalse();
    }

    @Test
    @DisplayName("refills over time based on the refill rate")
    void refillsOverTime() {
        long t0 = 0L;
        TokenBucket bucket = new TokenBucket(10, 5.0, t0);
        assertThat(bucket.tryConsume(10, t0)).isTrue();
        assertThat(bucket.tryConsume(1, t0)).isFalse();

        // after 1 second at 5 tokens/sec, 5 tokens are available
        long oneSecond = t0 + 1_000_000_000L;
        assertThat(bucket.tryConsume(5, oneSecond)).isTrue();
        assertThat(bucket.tryConsume(1, oneSecond)).isFalse();
    }

    @Test
    @DisplayName("refill is capped at capacity")
    void refillCappedAtCapacity() {
        long t0 = 0L;
        TokenBucket bucket = new TokenBucket(10, 100.0, t0);
        assertThat(bucket.tryConsume(10, t0)).isTrue();
        // a long time passes; tokens must not exceed capacity
        long farFuture = t0 + 60_000_000_000L;
        assertThat(bucket.availableTokensAt(farFuture)).isEqualTo(10.0);
    }

    @Test
    @DisplayName("cost below one is treated as one token")
    void costFloorIsOne() {
        long t0 = 0L;
        TokenBucket bucket = new TokenBucket(2, 1.0, t0);
        assertThat(bucket.tryConsume(0, t0)).isTrue();
        assertThat(bucket.tryConsume(-5, t0)).isTrue();
        assertThat(bucket.tryConsume(0, t0)).isFalse();
    }

    @Test
    @DisplayName("public no-arg constructor and tryConsume work against the system clock")
    void publicApiWorks() {
        TokenBucket bucket = new TokenBucket(5, 1.0);
        assertThat(bucket.tryConsume(5)).isTrue();
        assertThat(bucket.tryConsume(1)).isFalse();
        assertThat(bucket.availableTokens()).isLessThan(1.0);
    }

    @Test
    @DisplayName("rejects non-positive capacity and refill")
    void rejectsInvalidArgs() {
        assertThatThrownBy(() -> new TokenBucket(0, 1.0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenBucket(1, 0.0)).isInstanceOf(IllegalArgumentException.class);
    }
}
