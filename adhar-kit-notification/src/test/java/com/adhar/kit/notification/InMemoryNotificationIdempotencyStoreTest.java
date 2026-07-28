package com.adhar.kit.notification;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InMemoryNotificationIdempotencyStore")
class InMemoryNotificationIdempotencyStoreTest {

    @Test
    @DisplayName("first registration is new, immediate duplicate is suppressed")
    void firstRegistrationNewDuplicateSuppressed() {
        AtomicLong now = new AtomicLong(0);
        InMemoryNotificationIdempotencyStore store = new InMemoryNotificationIdempotencyStore(1000, now::get);

        assertThat(store.register("k1")).isTrue();
        assertThat(store.register("k1")).isFalse();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("key can be registered again after its TTL expires")
    void keyReRegistrableAfterTtl() {
        AtomicLong now = new AtomicLong(0);
        InMemoryNotificationIdempotencyStore store = new InMemoryNotificationIdempotencyStore(1000, now::get);

        assertThat(store.register("k1")).isTrue();
        now.set(1000); // expiry reached (expiry <= now)
        assertThat(store.register("k1")).isTrue();
    }

    @Test
    @DisplayName("distinct keys are tracked independently")
    void distinctKeys() {
        AtomicLong now = new AtomicLong(0);
        InMemoryNotificationIdempotencyStore store = new InMemoryNotificationIdempotencyStore(1000, now::get);

        assertThat(store.register("k1")).isTrue();
        assertThat(store.register("k2")).isTrue();
        assertThat(store.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("expired keys are purged lazily on registration")
    void expiredKeysPurged() {
        AtomicLong now = new AtomicLong(0);
        InMemoryNotificationIdempotencyStore store = new InMemoryNotificationIdempotencyStore(1000, now::get);
        store.register("k1");

        now.set(2000); // k1 expired
        store.register("k2"); // triggers purge of k1

        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("clear removes all keys")
    void clear() {
        InMemoryNotificationIdempotencyStore store = new InMemoryNotificationIdempotencyStore(1000);
        store.register("k1");
        store.register("k2");

        store.clear();

        assertThat(store.size()).isZero();
        assertThat(store.register("k1")).isTrue();
    }

    @Test
    @DisplayName("default constructor uses the system clock")
    void defaultConstructor() {
        InMemoryNotificationIdempotencyStore store = new InMemoryNotificationIdempotencyStore(60000);
        assertThat(store.register("k1")).isTrue();
        assertThat(store.register("k1")).isFalse();
    }
}
