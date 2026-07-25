package com.adhar.kit.graphql.persisted;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link InMemoryPersistedQueryCache}.
 */
class InMemoryPersistedQueryCacheTest {

    private InMemoryPersistedQueryCache cache;

    @BeforeEach
    void setUp() {
        cache = new InMemoryPersistedQueryCache(2);
    }

    @Test
    @DisplayName("returns empty for an unknown hash")
    void missReturnsEmpty() {
        assertThat(cache.get("unknown")).isEmpty();
    }

    @Test
    @DisplayName("returns empty for a null hash")
    void nullHashReturnsEmpty() {
        assertThat(cache.get(null)).isEmpty();
    }

    @Test
    @DisplayName("stores and retrieves a query by hash")
    void putAndGet() {
        cache.put("hash1", "query { hello }");

        assertThat(cache.get("hash1")).contains("query { hello }");
        assertThat(cache.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("evicts the least-recently-used entry once max size is exceeded")
    void evictsLeastRecentlyUsed() {
        cache.put("hash1", "query { a }");
        cache.put("hash2", "query { b }");
        // touch hash1 so it becomes most-recently-used
        assertThat(cache.get("hash1")).isPresent();

        cache.put("hash3", "query { c }");

        assertThat(cache.size()).isEqualTo(2);
        assertThat(cache.get("hash2")).as("least recently used entry should be evicted").isEmpty();
        assertThat(cache.get("hash1")).isPresent();
        assertThat(cache.get("hash3")).isPresent();
    }

    @Test
    @DisplayName("rejects a non-positive max size")
    void rejectsNonPositiveMaxSize() {
        assertThatThrownBy(() -> new InMemoryPersistedQueryCache(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a blank hash on put")
    void rejectsBlankHashOnPut() {
        assertThatThrownBy(() -> cache.put("  ", "query { a }"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("rejects a blank query on put")
    void rejectsBlankQueryOnPut() {
        assertThatThrownBy(() -> cache.put("hash1", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("reports zero size when empty")
    void emptySizeIsZero() {
        assertThat(cache.size()).isZero();
    }

    @Test
    @DisplayName("re-putting the same hash overwrites the value without growing size")
    void overwriteSameHash() {
        cache.put("hash1", "query { a }");
        cache.put("hash1", "query { a, updated }");

        assertThat(cache.size()).isEqualTo(1);
        Optional<String> value = cache.get("hash1");
        assertThat(value).contains("query { a, updated }");
    }
}
