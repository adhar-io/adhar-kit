package com.adhar.adharkit.cache.warmup;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.annotation.CacheWarmup;
import com.adhar.adharkit.cache.manager.CacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheWarmupProcessor} covering the synchronous and
 * asynchronous warmup paths, the annotated-method and dedicated-method
 * strategies, and error handling.
 */
@DisplayName("CacheWarmupProcessor Tests")
class CacheWarmupProcessorTest {

    private CacheManager cacheManager;
    private ScheduledExecutorService executor;
    private CacheWarmupProcessor processor;
    private WarmupBean bean;

    static class WarmupBean {
        final AtomicBoolean noArgWarmCalled = new AtomicBoolean();

        @CacheWarmup(cacheName = "wu-map", async = false)
        public Map<String, String> loadMap() {
            return Map.of("k1", "v1", "k2", "v2");
        }

        @CacheWarmup(cacheName = "wu-single", async = false)
        public String loadSingle() {
            return "single-value";
        }

        @CacheWarmup(cacheName = "wu-null", async = false)
        public String loadNull() {
            return null;
        }

        @CacheWarmup(cacheName = "wu-method", warmupMethod = "doWarm", async = false)
        public void triggerWarm() {
        }

        public void doWarm(CacheFacade cache) {
            cache.put("wk", "wv");
        }

        @CacheWarmup(cacheName = "wu-method-noarg", warmupMethod = "doWarmNoArg", async = false)
        public void triggerWarmNoArg() {
        }

        public void doWarmNoArg() {
            noArgWarmCalled.set(true);
        }

        @CacheWarmup(cacheName = "wu-method-throw", warmupMethod = "doWarmThrow", async = false)
        public void triggerWarmThrow() {
        }

        public void doWarmThrow(CacheFacade cache) {
            throw new RuntimeException("warm-boom");
        }

        @CacheWarmup(cacheName = "wu-badmethod", warmupMethod = "missingMethod", async = false)
        public void triggerBad() {
        }

        @CacheWarmup(cacheName = "wu-args", async = false)
        public String needsArgs(String x) {
            return x;
        }

        @CacheWarmup(cacheName = "wu-throw", async = false)
        public Map<String, String> loadThrows() {
            throw new RuntimeException("load-fail");
        }

        @CacheWarmup(cacheName = "wu-delay", async = false, delay = 20)
        public String loadDelayed() {
            return "delayed";
        }

        @CacheWarmup(cacheName = "wu-async", async = true, delay = 0)
        public Map<String, String> loadAsync() {
            return Map.of("a", "1");
        }
    }

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        for (String name : new String[]{"wu-map", "wu-single", "wu-null", "wu-method",
            "wu-method-noarg", "wu-method-throw", "wu-badmethod", "wu-args", "wu-throw",
            "wu-delay", "wu-async"}) {
            cacheManager.removeCache(name);
        }
        executor = Executors.newScheduledThreadPool(2);
        processor = new CacheWarmupProcessor(cacheManager, executor);
        bean = new WarmupBean();
    }

    @AfterEach
    void tearDown() {
        processor.close();
    }

    private void warmupSync(String methodName) {
        Method method = findMethod(methodName);
        processor.warmup(bean, method, method.getAnnotation(CacheWarmup.class));
    }

    private Method findMethod(String name) {
        for (Method m : WarmupBean.class.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        throw new IllegalArgumentException("no method " + name);
    }

    @Test
    @DisplayName("a Map result is bulk-loaded into the cache")
    void mapResultBulkLoaded() {
        warmupSync("loadMap");
        CacheFacade cache = cacheManager.getCache("wu-map");
        assertEquals("v1", cache.get("k1"));
        assertEquals("v2", cache.get("k2"));
        assertEquals(1, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("a non-Map result is stored under the method name")
    void singleResultStored() {
        warmupSync("loadSingle");
        assertEquals("single-value", cacheManager.getCache("wu-single").get("loadSingle"));
        assertEquals(1, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("a null result leaves the cache empty but counts as completed")
    void nullResultLeavesCacheEmpty() {
        warmupSync("loadNull");
        assertEquals(0, cacheManager.getCache("wu-null").size());
        assertEquals(1, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("a dedicated warmup method receiving the CacheFacade is invoked")
    void dedicatedWarmupMethodWithCache() {
        warmupSync("triggerWarm");
        assertEquals("wv", cacheManager.getCache("wu-method").get("wk"));
        assertEquals(1, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("a no-argument dedicated warmup method is invoked")
    void dedicatedWarmupMethodNoArg() {
        warmupSync("triggerWarmNoArg");
        assertTrue(bean.noArgWarmCalled.get());
        assertEquals(1, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("a throwing dedicated warmup method is swallowed")
    void dedicatedWarmupMethodThrows() {
        assertDoesNotThrow(() -> warmupSync("triggerWarmThrow"));
        assertEquals(0, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("an unresolvable warmup method leaves the cache un-warmed")
    void missingWarmupMethod() {
        assertDoesNotThrow(() -> warmupSync("triggerBad"));
        assertEquals(0, cacheManager.getCache("wu-badmethod").size());
        assertEquals(0, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("an annotated method needing arguments with no warmup method is skipped")
    void annotatedMethodNeedingArgsSkipped() {
        assertDoesNotThrow(() -> warmupSync("needsArgs"));
        assertEquals(0, cacheManager.getCache("wu-args").size());
        assertEquals(0, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("an exception thrown by the annotated method is swallowed")
    void annotatedMethodThrows() {
        assertDoesNotThrow(() -> warmupSync("loadThrows"));
        assertEquals(0, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("a synchronous delay is honored before warming")
    void synchronousDelay() {
        warmupSync("loadDelayed");
        assertEquals("delayed", cacheManager.getCache("wu-delay").get("loadDelayed"));
        assertEquals(1, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("an async warmup runs on the executor")
    void asyncWarmup() {
        Method method = findMethod("loadAsync");
        processor.warmup(bean, method, method.getAnnotation(CacheWarmup.class));
        await().atMost(3, TimeUnit.SECONDS).until(() -> {
            CacheFacade cache = cacheManager.getCache("wu-async");
            return cache != null && "1".equals(cache.get("a"));
        });
        assertEquals(1, processor.getCompletedWarmupCount());
    }

    @Test
    @DisplayName("warmupAll scans the context and warms every annotated bean method")
    void warmupAllScansContext() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("warmupBean", WarmupBean.class, () -> bean);
            context.refresh();

            processor.warmupAll(context);

            await().atMost(3, TimeUnit.SECONDS).until(() -> {
                CacheFacade mapCache = cacheManager.getCache("wu-map");
                CacheFacade asyncCache = cacheManager.getCache("wu-async");
                return mapCache != null && "v1".equals(mapCache.get("k1"))
                    && asyncCache != null && "1".equals(asyncCache.get("a"));
            });
        }
    }

    @Test
    @DisplayName("onApplicationEvent-style warmup drives warmupAll")
    void warmupAllViaContext() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("warmupBean", WarmupBean.class, () -> bean);
            context.refresh();
            assertDoesNotThrow(() -> processor.warmupAll(context));
        }
    }

    @Test
    @DisplayName("the default constructor builds a working daemon executor")
    void defaultConstructor() {
        CacheWarmupProcessor defaultProcessor = new CacheWarmupProcessor(cacheManager);
        try {
            Method method = findMethod("loadAsync");
            defaultProcessor.warmup(bean, method, method.getAnnotation(CacheWarmup.class));
            await().atMost(3, TimeUnit.SECONDS).until(() -> {
                CacheFacade cache = cacheManager.getCache("wu-async");
                return cache != null && "1".equals(cache.get("a"));
            });
        } finally {
            defaultProcessor.close();
        }
    }
}
