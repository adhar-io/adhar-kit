package com.adhar.kit.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lazy Tests")
class LazyTest {

    @Test
    @DisplayName("get() initializes value on first call and caches it")
    void get_initializesOnce() {
        AtomicInteger counter = new AtomicInteger(0);
        Lazy<String> lazy = Lazy.of(() -> {
            counter.incrementAndGet();
            return "computed";
        });

        assertThat(lazy.isInitialized()).isFalse();

        String val1 = lazy.get();
        String val2 = lazy.get();

        assertThat(val1).isEqualTo("computed");
        assertThat(val2).isEqualTo("computed");
        assertThat(counter.get()).isEqualTo(1);
        assertThat(lazy.isInitialized()).isTrue();
    }

    @Test
    @DisplayName("reset() allows re-initialization")
    void reset_allowsReInit() {
        AtomicInteger counter = new AtomicInteger(0);
        Lazy<Integer> lazy = Lazy.of(counter::incrementAndGet);

        assertThat(lazy.get()).isEqualTo(1);
        lazy.reset();
        assertThat(lazy.isInitialized()).isFalse();
        assertThat(lazy.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("getIfInitialized() returns null before init, value after")
    void getIfInitialized() {
        Lazy<String> lazy = Lazy.of(() -> "hello");
        assertThat(lazy.getIfInitialized()).isNull();
        lazy.get();
        assertThat(lazy.getIfInitialized()).isEqualTo("hello");
    }
}
