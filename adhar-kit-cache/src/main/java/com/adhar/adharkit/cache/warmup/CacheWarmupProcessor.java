package com.adhar.adharkit.cache.warmup;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.annotation.CacheWarmup;
import com.adhar.adharkit.cache.manager.CacheManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.util.ClassUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runtime backing the {@link CacheWarmup} annotation: on
 * {@link ApplicationReadyEvent}, every bean method annotated with
 * {@code @CacheWarmup} is executed to pre-populate its cache, asynchronously on
 * a dedicated daemon executor ({@code adhar-cache-warmup-N} threads).
 *
 * <p>Per-annotation semantics:</p>
 * <ul>
 *   <li>{@code cacheName} - the cache is created (via
 *       {@link CacheManager#getOrCreateCache(String)}) before warmup runs.</li>
 *   <li>{@code warmupMethod} - when non-blank, a method of that name on the same
 *       bean performs the warmup; a single-argument {@code (CacheFacade)}
 *       signature is preferred (invoked with the target cache), otherwise a
 *       no-argument signature is used.</li>
 *   <li>When {@code warmupMethod} is blank, the annotated method itself is
 *       invoked (it must take no arguments): a {@link Map} result is bulk-loaded
 *       into the cache via {@code putAll}; any other non-null result is stored
 *       under the method name.</li>
 *   <li>{@code async} - {@code true} (default) schedules the warmup on the
 *       dedicated executor; {@code false} runs it synchronously in the caller.</li>
 *   <li>{@code delay} - milliseconds to wait before executing the warmup
 *       (scheduler delay when async, a sleep when synchronous).</li>
 * </ul>
 *
 * <p>Warmup failures are logged and never prevent application startup.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class CacheWarmupProcessor implements ApplicationListener<ApplicationReadyEvent>, AutoCloseable {

    private final CacheManager cacheManager;
    private final ScheduledExecutorService executor;
    private final AtomicInteger completedWarmups = new AtomicInteger();

    /**
     * Creates the processor with a 2-thread daemon executor.
     *
     * @param cacheManager the cache manager receiving warmed values
     */
    public CacheWarmupProcessor(CacheManager cacheManager) {
        this(cacheManager, Executors.newScheduledThreadPool(2, daemonThreadFactory()));
    }

    /**
     * Creates the processor with a caller-supplied executor (useful for tests).
     *
     * @param cacheManager the cache manager receiving warmed values
     * @param executor     the warmup executor
     */
    public CacheWarmupProcessor(CacheManager cacheManager, ScheduledExecutorService executor) {
        this.cacheManager = cacheManager;
        this.executor = executor;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        warmupAll(event.getApplicationContext());
    }

    /**
     * Scans every singleton bean in the context for {@link CacheWarmup} methods
     * and triggers their warmups.
     *
     * @param context the application context to scan
     */
    public void warmupAll(ApplicationContext context) {
        for (String beanName : context.getBeanDefinitionNames()) {
            Class<?> type;
            try {
                type = context.getType(beanName, false);
            } catch (RuntimeException e) {
                continue;
            }
            if (type == null) {
                continue;
            }
            Class<?> userClass = ClassUtils.getUserClass(type);
            for (Method method : userClass.getDeclaredMethods()) {
                CacheWarmup warmup = method.getAnnotation(CacheWarmup.class);
                if (warmup != null) {
                    warmup(context.getBean(beanName), method, warmup);
                }
            }
        }
    }

    /**
     * Triggers a single warmup, honoring {@code async} and {@code delay}.
     *
     * @param bean   the bean owning the warmup
     * @param method the annotated method
     * @param warmup the annotation instance
     */
    public void warmup(Object bean, Method method, CacheWarmup warmup) {
        long delayMillis = Math.max(0, warmup.delay());
        if (warmup.async()) {
            executor.schedule(() -> executeWarmup(bean, method, warmup), delayMillis, TimeUnit.MILLISECONDS);
            log.debug("Scheduled async warmup of cache '{}' via {} (delay {} ms)",
                warmup.cacheName(), method.getName(), delayMillis);
            return;
        }
        if (delayMillis > 0) {
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted before warmup of cache '{}'", warmup.cacheName());
                return;
            }
        }
        executeWarmup(bean, method, warmup);
    }

    /**
     * Executes the warmup itself. Failures are logged, never thrown.
     */
    void executeWarmup(Object bean, Method method, CacheWarmup warmup) {
        try {
            CacheFacade cache = cacheManager.getOrCreateCache(warmup.cacheName());
            if (!warmup.warmupMethod().isBlank()) {
                invokeWarmupMethod(bean, warmup, cache);
                return;
            }
            if (method.getParameterCount() > 0) {
                log.warn("Cannot warm cache '{}': annotated method {} requires arguments and no "
                    + "warmupMethod is configured", warmup.cacheName(), method.getName());
                return;
            }
            method.setAccessible(true);
            Object result = method.invoke(bean);
            if (result instanceof Map<?, ?> map) {
                cache.putAll(map);
                log.info("Warmed cache '{}' with {} entries from {}",
                    warmup.cacheName(), map.size(), method.getName());
            } else if (result != null) {
                cache.put(method.getName(), result);
                log.info("Warmed cache '{}' with result of {}", warmup.cacheName(), method.getName());
            } else {
                log.debug("Warmup method {} returned null; cache '{}' left as-is",
                    method.getName(), warmup.cacheName());
            }
            completedWarmups.incrementAndGet();
        } catch (Exception e) {
            Throwable cause = e instanceof InvocationTargetException ite && ite.getCause() != null
                ? ite.getCause() : e;
            log.warn("Cache warmup failed for cache '{}' via {}: {}",
                warmup.cacheName(), method.getName(), cause.toString());
        }
    }

    /**
     * Invokes the configured {@code warmupMethod}, preferring the
     * {@code (CacheFacade)} signature over the no-argument one.
     */
    private void invokeWarmupMethod(Object bean, CacheWarmup warmup, CacheFacade cache) throws Exception {
        Method warmupMethod = resolveWarmupMethod(bean.getClass(), warmup.warmupMethod());
        if (warmupMethod == null) {
            log.warn("Warmup method '{}' not found on {}; cache '{}' not warmed",
                warmup.warmupMethod(), bean.getClass().getName(), warmup.cacheName());
            return;
        }
        warmupMethod.setAccessible(true);
        if (warmupMethod.getParameterCount() == 1) {
            warmupMethod.invoke(bean, cache);
        } else {
            warmupMethod.invoke(bean);
        }
        completedWarmups.incrementAndGet();
        log.info("Warmed cache '{}' via warmup method {}", warmup.cacheName(), warmupMethod.getName());
    }

    private static Method resolveWarmupMethod(Class<?> type, String name) {
        Method noArg = null;
        for (Class<?> current = type; current != null && current != Object.class;
             current = current.getSuperclass()) {
            for (Method candidate : current.getDeclaredMethods()) {
                if (!candidate.getName().equals(name)) {
                    continue;
                }
                if (candidate.getParameterCount() == 1
                    && CacheFacade.class.isAssignableFrom(candidate.getParameterTypes()[0])) {
                    return candidate;
                }
                if (candidate.getParameterCount() == 0 && noArg == null) {
                    noArg = candidate;
                }
            }
        }
        return noArg;
    }

    /**
     * Number of successfully completed warmups (visible for tests/monitoring).
     *
     * @return completed warmup count
     */
    public int getCompletedWarmupCount() {
        return completedWarmups.get();
    }

    /**
     * Shuts the warmup executor down.
     */
    @Override
    public void close() {
        executor.shutdownNow();
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "adhar-cache-warmup-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
