package com.adhar.kit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Memoizer Tests")
class MemoizerTest {

    @Test
    @DisplayName("caches computed values and returns cached on second call")
    void get_cachesValue() {
        AtomicInteger computeCount = new AtomicInteger(0);
        Memoizer<String, Integer> memo = new Memoizer<>();

        Integer val1 = memo.get("key", k -> {
            computeCount.incrementAndGet();
            return 42;
        });
        Integer val2 = memo.get("key", k -> {
            computeCount.incrementAndGet();
            return 99;
        });

        assertThat(val1).isEqualTo(42);
        assertThat(val2).isEqualTo(42);
        assertThat(computeCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("invalidate removes cached entry")
    void invalidate_removesEntry() {
        Memoizer<String, String> memo = new Memoizer<>();
        memo.get("k", k -> "v");
        assertThat(memo.containsKey("k")).isTrue();

        memo.invalidate("k");
        assertThat(memo.containsKey("k")).isFalse();
        assertThat(memo.size()).isZero();
    }

    @Test
    @DisplayName("clear removes all entries")
    void clear_removesAll() {
        Memoizer<String, String> memo = new Memoizer<>();
        memo.get("a", k -> "1");
        memo.get("b", k -> "2");
        assertThat(memo.size()).isEqualTo(2);

        memo.clear();
        assertThat(memo.size()).isZero();
    }

    @Test
    @DisplayName("TTL causes re-computation after expiry")
    void ttl_recomputes() throws InterruptedException {
        AtomicInteger counter = new AtomicInteger(0);
        Memoizer<String, Integer> memo = new Memoizer<>(50); // 50ms TTL

        memo.get("k", k -> counter.incrementAndGet());
        assertThat(counter.get()).isEqualTo(1);

        Thread.sleep(100); // Wait for TTL to expire

        memo.get("k", k -> counter.incrementAndGet());
        assertThat(counter.get()).isEqualTo(2);
    }
}
