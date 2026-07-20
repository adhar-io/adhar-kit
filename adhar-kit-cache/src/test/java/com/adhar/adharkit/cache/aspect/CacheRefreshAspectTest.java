package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.CacheRefresh;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.adharkit.cache.refresh.CacheRefreshScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheRefreshAspect}.
 */
@DisplayName("CacheRefreshAspect Tests")
class CacheRefreshAspectTest {

    private CacheManager cacheManager;
    private CacheRefreshScheduler scheduler;
    private CacheRefreshAspect aspect;
    private ConfigService service;

    /**
     * Real annotated sample class. initialDelay=0 makes the first background
     * refresh run promptly, which the test awaits.
     */
    static class ConfigService {
        final AtomicInteger invocations = new AtomicInteger();

        @CacheRefresh(cacheName = "cr-config", refreshInterval = 1, initialDelay = 0)
        public String getConfig(String configKey) {
            invocations.incrementAndGet();
            return "config-" + configKey + "-" + invocations.get();
        }

        @CacheRefresh(cacheName = "cr-failing", refreshInterval = 1, initialDelay = 1)
        public String getFailingConfig(String configKey) {
            invocations.incrementAndGet();
            throw new IllegalStateException("refresh-boom");
        }
    }

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        scheduler = new CacheRefreshScheduler(cacheManager);
        aspect = new CacheRefreshAspect(scheduler, new CacheKeyGenerator());
        service = new ConfigService();
    }

    @AfterEach
    void tearDown() {
        scheduler.close();
    }

    @Test
    @DisplayName("first invocation returns the result and registers a refresh task")
    void registersRefreshOnFirstCall() throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(service, "getConfig", "app");
        Object result = aspect.aroundCacheRefresh(joinPoint,
            joinPoint.method().getAnnotation(CacheRefresh.class));

        assertEquals("config-app-1", result);
        assertEquals(1, joinPoint.proceedCount.get());
        assertEquals(1, scheduler.getRegisteredCount());

        // initialDelay=0: the background refresh re-invokes the raw method and stores the value
        await().atMost(5, TimeUnit.SECONDS).until(() -> service.invocations.get() >= 2);
        CacheKeyGenerator keyGenerator = new CacheKeyGenerator();
        Object key = keyGenerator.generate(null, joinPoint.method(), service, new Object[]{"app"});
        await().atMost(5, TimeUnit.SECONDS)
            .until(() -> cacheManager.getOrCreateCache("cr-config").get(key) != null);
    }

    @Test
    @DisplayName("repeat invocations do not register duplicate refresh tasks")
    void duplicateRegistrationsIgnored() throws Throwable {
        for (int i = 0; i < 3; i++) {
            TestJoinPoint joinPoint = new TestJoinPoint(service, "getFailingConfig", "dup");
            assertThrows(IllegalStateException.class, () -> aspect.aroundCacheRefresh(joinPoint,
                joinPoint.method().getAnnotation(CacheRefresh.class)));
        }
        // method always throws, so nothing was registered (proceed happens first)
        assertEquals(0, scheduler.getRegisteredCount());

        TestJoinPoint ok1 = new TestJoinPoint(service, "getConfig", "dup");
        aspect.aroundCacheRefresh(ok1, ok1.method().getAnnotation(CacheRefresh.class));
        TestJoinPoint ok2 = new TestJoinPoint(service, "getConfig", "dup");
        aspect.aroundCacheRefresh(ok2, ok2.method().getAnnotation(CacheRefresh.class));
        assertEquals(1, scheduler.getRegisteredCount(), "same args must map to one refresh task");

        TestJoinPoint other = new TestJoinPoint(service, "getConfig", "other");
        aspect.aroundCacheRefresh(other, other.method().getAnnotation(CacheRefresh.class));
        assertEquals(2, scheduler.getRegisteredCount(), "different args register separately");
    }

    @Test
    @DisplayName("buildLoader re-invokes the raw target method")
    void buildLoaderInvokesMethod() {
        Method method = TestJoinPoint.findMethod(ConfigService.class, "getConfig");
        Supplier<Object> loader = aspect.buildLoader(method, service, new Object[]{"x"});

        assertEquals("config-x-1", loader.get());
        assertEquals("config-x-2", loader.get());
    }

    @Test
    @DisplayName("buildLoader wraps method exceptions")
    void buildLoaderWrapsExceptions() {
        Method method = TestJoinPoint.findMethod(ConfigService.class, "getFailingConfig");
        Supplier<Object> loader = aspect.buildLoader(method, service, new Object[]{"x"});

        IllegalStateException ex = assertThrows(IllegalStateException.class, loader::get);
        assertTrue(ex.getMessage().contains("Cache refresh invocation failed")
            || "refresh-boom".equals(ex.getMessage()));
    }
}
