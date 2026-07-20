package com.adhar.adharkit.cache.refresh;

import com.adhar.adharkit.cache.manager.CacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheRefreshScheduler}.
 */
@DisplayName("CacheRefreshScheduler Tests")
class CacheRefreshSchedulerTest {

    private CacheManager cacheManager;
    private CacheRefreshScheduler scheduler;
    private String cacheName;

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        scheduler = new CacheRefreshScheduler(cacheManager);
        cacheName = "refresh-" + UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Test
    @DisplayName("registered entries are refreshed repeatedly at the interval")
    void refreshesAtInterval() {
        AtomicInteger loads = new AtomicInteger();
        boolean registered = scheduler.register(cacheName, "k1",
            Duration.ZERO, Duration.ofMillis(50), () -> "v-" + loads.incrementAndGet());

        assertTrue(registered);
        assertEquals(1, scheduler.getRegisteredCount());

        await().atMost(5, TimeUnit.SECONDS).until(() -> loads.get() >= 3);
        Object value = cacheManager.getOrCreateCache(cacheName).get("k1");
        assertNotNull(value);
        assertTrue(value.toString().startsWith("v-"));
    }

    @Test
    @DisplayName("duplicate registration for the same cache/key is ignored")
    void duplicateRegistrationIgnored() {
        assertTrue(scheduler.register(cacheName, "k1", Duration.ofMinutes(1),
            Duration.ofMinutes(1), () -> "a"));
        assertFalse(scheduler.register(cacheName, "k1", Duration.ofMinutes(1),
            Duration.ofMinutes(1), () -> "b"));
        assertEquals(1, scheduler.getRegisteredCount());
    }

    @Test
    @DisplayName("distinct keys register independent tasks")
    void distinctKeys() {
        scheduler.register(cacheName, "k1", Duration.ofMinutes(1), Duration.ofMinutes(1), () -> "a");
        scheduler.register(cacheName, "k2", Duration.ofMinutes(1), Duration.ofMinutes(1), () -> "b");
        assertEquals(2, scheduler.getRegisteredCount());
    }

    @Test
    @DisplayName("refresh stores non-null values immediately")
    void refreshNowStoresValue() {
        scheduler.refresh(cacheName, "k1", () -> "fresh");
        assertEquals("fresh", cacheManager.getCache(cacheName).get("k1"));
    }

    @Test
    @DisplayName("refresh skips null loader results")
    void refreshSkipsNull() {
        scheduler.refresh(cacheName, "k1", () -> null);
        assertNull(cacheManager.getOrCreateCache(cacheName).get("k1"));
    }

    @Test
    @DisplayName("refresh swallows loader exceptions")
    void refreshSwallowsExceptions() {
        assertDoesNotThrow(() -> scheduler.refresh(cacheName, "k1", () -> {
            throw new IllegalStateException("loader-boom");
        }));
    }

    @Test
    @DisplayName("a throwing loader does not kill the periodic task")
    void periodicTaskSurvivesExceptions() {
        AtomicInteger attempts = new AtomicInteger();
        scheduler.register(cacheName, "k1", Duration.ZERO, Duration.ofMillis(50), () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("boom");
        });
        await().atMost(5, TimeUnit.SECONDS).until(() -> attempts.get() >= 2);
    }

    @Test
    @DisplayName("cancel stops the task and allows re-registration")
    void cancelStopsTask() {
        scheduler.register(cacheName, "k1", Duration.ofMinutes(1), Duration.ofMinutes(1), () -> "a");
        assertTrue(scheduler.cancel(cacheName, "k1"));
        assertEquals(0, scheduler.getRegisteredCount());
        assertFalse(scheduler.cancel(cacheName, "k1"), "second cancel has nothing to remove");
        assertTrue(scheduler.register(cacheName, "k1", Duration.ofMinutes(1),
            Duration.ofMinutes(1), () -> "b"));
    }

    @Test
    @DisplayName("close cancels everything and stops refreshing")
    void closeStopsAll() throws Exception {
        AtomicInteger loads = new AtomicInteger();
        scheduler.register(cacheName, "k1", Duration.ZERO, Duration.ofMillis(30),
            () -> "v-" + loads.incrementAndGet());
        await().atMost(5, TimeUnit.SECONDS).until(() -> loads.get() >= 1);

        scheduler.close();
        assertEquals(0, scheduler.getRegisteredCount());
        int after = loads.get();
        Thread.sleep(200);
        assertTrue(loads.get() <= after + 1, "no further refreshes after close");
    }
}
