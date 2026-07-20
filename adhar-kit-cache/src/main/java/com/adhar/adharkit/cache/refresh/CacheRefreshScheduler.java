package com.adhar.adharkit.cache.refresh;

import com.adhar.adharkit.cache.manager.CacheManager;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Background refresher backing the {@code @CacheRefresh} annotation.
 *
 * <p>Refreshable entries (cache, key, loader) are registered on first method
 * invocation (by {@code CacheRefreshAspect}) or programmatically; this scheduler
 * then re-loads each entry at its interval and writes the fresh value into the
 * cache, keeping entries warm ahead of expiry.</p>
 *
 * <p>Uses a plain {@link ScheduledExecutorService} with daemon threads — no
 * dependency on Spring's {@code @Scheduled} infrastructure.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class CacheRefreshScheduler implements AutoCloseable {

    private final CacheManager cacheManager;
    private final ScheduledExecutorService executor;
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    /**
     * Creates a scheduler with a 2-thread daemon executor.
     *
     * @param cacheManager the cache manager receiving refreshed values
     */
    public CacheRefreshScheduler(CacheManager cacheManager) {
        this(cacheManager, Executors.newScheduledThreadPool(2, daemonThreadFactory()));
    }

    /**
     * Creates a scheduler with a caller-supplied executor (useful for tests).
     *
     * @param cacheManager the cache manager receiving refreshed values
     * @param executor     the scheduling executor
     */
    public CacheRefreshScheduler(CacheManager cacheManager, ScheduledExecutorService executor) {
        this.cacheManager = cacheManager;
        this.executor = executor;
    }

    /**
     * Registers a refreshable entry. Idempotent per (cacheName, key): repeat
     * registrations are ignored while a task is active.
     *
     * @param cacheName    target cache name
     * @param key          cache key to refresh
     * @param initialDelay delay before the first refresh
     * @param interval     refresh period
     * @param loader       supplies the fresh value (a null result is skipped)
     * @return true if a new refresh task was scheduled, false if one already exists
     */
    public boolean register(String cacheName, Object key, Duration initialDelay,
                            Duration interval, Supplier<Object> loader) {
        String taskId = taskId(cacheName, key);
        ScheduledFuture<?>[] created = new ScheduledFuture<?>[1];
        tasks.computeIfAbsent(taskId, id -> {
            log.debug("Scheduling cache refresh for {} every {}", id, interval);
            created[0] = executor.scheduleAtFixedRate(
                () -> refresh(cacheName, key, loader),
                Math.max(0, initialDelay.toMillis()),
                Math.max(1, interval.toMillis()),
                TimeUnit.MILLISECONDS);
            return created[0];
        });
        return created[0] != null;
    }

    /**
     * Refreshes a single entry immediately by invoking its loader and storing
     * the result.
     *
     * @param cacheName target cache name
     * @param key       cache key
     * @param loader    value supplier
     */
    public void refresh(String cacheName, Object key, Supplier<Object> loader) {
        try {
            Object value = loader.get();
            if (value != null) {
                cacheManager.getOrCreateCache(cacheName).put(key, value);
                log.trace("Refreshed cache '{}' key={}", cacheName, key);
            } else {
                log.trace("Refresh loader returned null for cache '{}' key={}; skipping", cacheName, key);
            }
        } catch (Exception e) {
            log.warn("Cache refresh failed for cache '{}' key={}: {}", cacheName, key, e.getMessage());
        }
    }

    /**
     * Cancels the refresh task for an entry.
     *
     * @param cacheName target cache name
     * @param key       cache key
     * @return true if a task existed and was cancelled
     */
    public boolean cancel(String cacheName, Object key) {
        ScheduledFuture<?> task = tasks.remove(taskId(cacheName, key));
        if (task != null) {
            task.cancel(false);
            return true;
        }
        return false;
    }

    /**
     * Number of active refresh registrations.
     *
     * @return registration count
     */
    public int getRegisteredCount() {
        return tasks.size();
    }

    /**
     * Cancels all tasks and shuts the executor down.
     */
    @Override
    public void close() {
        tasks.values().forEach(task -> task.cancel(false));
        tasks.clear();
        executor.shutdownNow();
    }

    private static String taskId(String cacheName, Object key) {
        return cacheName + "::" + key;
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "adhar-cache-refresh-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
