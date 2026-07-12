package com.adhar.kit.core.util;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LazyRegistryTest {

    @Test
    void registerAndGetCreatesValueOnFirstAccess() {
        LazyRegistry registry = new LazyRegistry();
        AtomicInteger creations = new AtomicInteger();

        registry.register("db", () -> {
            creations.incrementAndGet();
            return "database";
        });

        // Registered but not initialized yet.
        assertTrue(registry.contains("db"));
        assertFalse(registry.isInitialized("db"));
        assertEquals(0, creations.get());

        String value = registry.get("db", String.class);
        assertEquals("database", value);
        assertEquals(1, creations.get());
        assertTrue(registry.isInitialized("db"));

        // Second access reuses the cached value.
        assertEquals("database", registry.get("db", String.class));
        assertEquals(1, creations.get());
    }

    @Test
    void getThrowsForUnknownKey() {
        LazyRegistry registry = new LazyRegistry();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            registry.get("missing", String.class));
        assertTrue(ex.getMessage().contains("missing"));
    }

    @Test
    void containsReturnsFalseForUnknownKey() {
        LazyRegistry registry = new LazyRegistry();
        assertFalse(registry.contains("nope"));
    }

    @Test
    void isInitializedReturnsFalseForUnknownKey() {
        LazyRegistry registry = new LazyRegistry();
        assertFalse(registry.isInitialized("nope"));
    }

    @Test
    void resetForcesRecreation() {
        LazyRegistry registry = new LazyRegistry();
        AtomicInteger creations = new AtomicInteger();

        registry.register("cache", () -> "cache-" + creations.incrementAndGet());

        assertEquals("cache-1", registry.get("cache", String.class));
        registry.reset("cache");
        assertFalse(registry.isInitialized("cache"));
        assertEquals("cache-2", registry.get("cache", String.class));
    }

    @Test
    void resetUnknownKeyIsNoOp() {
        LazyRegistry registry = new LazyRegistry();
        assertDoesNotThrow(() -> registry.reset("missing"));
    }

    @Test
    void resetAllResetsEveryValue() {
        LazyRegistry registry = new LazyRegistry();
        AtomicInteger a = new AtomicInteger();
        AtomicInteger b = new AtomicInteger();
        registry.register("a", () -> "a-" + a.incrementAndGet());
        registry.register("b", () -> "b-" + b.incrementAndGet());

        registry.get("a", String.class);
        registry.get("b", String.class);

        registry.resetAll();

        assertFalse(registry.isInitialized("a"));
        assertFalse(registry.isInitialized("b"));
        assertEquals("a-2", registry.get("a", String.class));
        assertEquals("b-2", registry.get("b", String.class));
    }

    @Test
    void clearRemovesAllRegistrations() {
        LazyRegistry registry = new LazyRegistry();
        registry.register("x", () -> "value");
        assertTrue(registry.contains("x"));

        registry.clear();

        assertFalse(registry.contains("x"));
    }
}
