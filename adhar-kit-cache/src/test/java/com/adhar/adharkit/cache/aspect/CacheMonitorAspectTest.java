package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.annotation.CacheMonitor;
import com.adhar.adharkit.cache.annotation.Cacheable;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.adharkit.cache.metrics.CacheMetricsBinder;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CacheMonitorAspect} covering hit/miss counters, the
 * latency timer, the alert meters and the metrics-binder integration.
 */
@DisplayName("CacheMonitorAspect Tests")
class CacheMonitorAspectTest {

    private static final String CLASS_TAG = MonitorSample.class.getSimpleName();

    private CacheManager cacheManager;
    private CacheKeyGenerator keyGenerator;
    private SimpleMeterRegistry registry;
    private CacheMetricsBinder metricsBinder;
    private CacheMonitorAspect aspect;
    private MonitorSample sample;

    static class MonitorSample {

        @CacheMonitor
        @Cacheable(cacheName = "mon", key = "#id")
        public String monitored(String id) {
            return "v-" + id;
        }

        @CacheMonitor(detailedLogging = true)
        @Cacheable(cacheName = "mon", key = "#id")
        public String monitoredDetailed(String id) {
            return "d-" + id;
        }

        @CacheMonitor(metricsPrefix = "custom")
        @Cacheable(cacheName = "mon", key = "#id")
        public String monitoredCustomPrefix(String id) {
            return "cp-" + id;
        }

        @CacheMonitor(metricsPrefix = "")
        @Cacheable(cacheName = "mon", key = "#id")
        public String monitoredBlankPrefix(String id) {
            return "bp-" + id;
        }

        @CacheMonitor
        public String notCacheable(String id) {
            return "n-" + id;
        }

        @CacheMonitor
        @Cacheable(key = "#id")
        public String monitoredDefaultName(String id) {
            return "dn-" + id;
        }

        @CacheMonitor
        @Cacheable(cacheName = "mon-missing", key = "#id")
        public String monitoredMissingCache(String id) {
            return "mm-" + id;
        }

        @CacheMonitor(alertOnHitRateBelow = 0.9, alertOnEvictionRateAbove = 1.0)
        @Cacheable(cacheName = "mon-hit", key = "#id")
        public String monitoredHitAlert(String id) {
            return "h-" + id;
        }

        @CacheMonitor(alertOnHitRateBelow = 0.0, alertOnEvictionRateAbove = 0.1)
        @Cacheable(cacheName = "mon-evict", key = "#id")
        public String monitoredEvictAlert(String id) {
            return "e-" + id;
        }
    }

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        for (String name : new String[]{"mon", "mon-hit", "mon-evict", "mon-missing"}) {
            cacheManager.removeCache(name);
        }
        keyGenerator = new CacheKeyGenerator();
        registry = new SimpleMeterRegistry();
        metricsBinder = new CacheMetricsBinder(cacheManager);
        metricsBinder.bindTo(registry);
        aspect = new CacheMonitorAspect(cacheManager, keyGenerator, registry, metricsBinder);
        sample = new MonitorSample();
        cacheManager.getOrCreateCache("mon");
    }

    private Object invoke(String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(sample, methodName, args);
        return aspect.aroundCacheMonitor(joinPoint,
            joinPoint.method().getAnnotation(CacheMonitor.class));
    }

    private Counter getsCounter(String prefix, String method, String result) {
        return registry.find(prefix + ".gets")
            .tag("method", CLASS_TAG + "." + method)
            .tag("result", result)
            .counter();
    }

    @Test
    @DisplayName("a miss increments the miss counter and records latency")
    void missCounterAndLatency() throws Throwable {
        assertEquals("v-1", invoke("monitored", "1"));
        Counter miss = getsCounter("cache", "monitored", "miss");
        assertNotNull(miss);
        assertEquals(1.0, miss.count());
        assertEquals(1, registry.find("cache.latency")
            .tag("method", CLASS_TAG + ".monitored").timer().count());
    }

    @Test
    @DisplayName("a hit increments the hit counter")
    void hitCounter() throws Throwable {
        cacheManager.getCache("mon").put("1", "cached");
        invoke("monitored", "1");
        Counter hit = getsCounter("cache", "monitored", "hit");
        assertNotNull(hit);
        assertEquals(1.0, hit.count());
        assertNull(getsCounter("cache", "monitored", "miss"));
    }

    @Test
    @DisplayName("a method without @Cacheable records latency but no hit/miss counter")
    void noCacheableNoGetsCounter() throws Throwable {
        assertEquals("n-1", invoke("notCacheable", "1"));
        assertNull(getsCounter("cache", "notCacheable", "hit"));
        assertNull(getsCounter("cache", "notCacheable", "miss"));
        assertEquals(1, registry.find("cache.latency")
            .tag("method", CLASS_TAG + ".notCacheable").timer().count());
    }

    @Test
    @DisplayName("a blank @Cacheable cacheName falls back to the method name")
    void defaultCacheNameFromMethod() throws Throwable {
        // cache 'monitoredDefaultName' does not exist => probe misses, no alert, no error
        assertEquals("dn-1", invoke("monitoredDefaultName", "1"));
        assertNotNull(getsCounter("cache", "monitoredDefaultName", "miss"));
    }

    @Test
    @DisplayName("a missing cache still records a miss and skips alerts")
    void missingCacheProbesMiss() throws Throwable {
        assertEquals("mm-1", invoke("monitoredMissingCache", "1"));
        assertNotNull(getsCounter("cache", "monitoredMissingCache", "miss"));
    }

    @Test
    @DisplayName("a custom metrics prefix is honored")
    void customPrefix() throws Throwable {
        invoke("monitoredCustomPrefix", "1");
        assertNotNull(getsCounter("custom", "monitoredCustomPrefix", "miss"));
        assertEquals(1, registry.find("custom.latency")
            .tag("method", CLASS_TAG + ".monitoredCustomPrefix").timer().count());
    }

    @Test
    @DisplayName("a blank metrics prefix defaults to 'cache'")
    void blankPrefixDefaults() throws Throwable {
        invoke("monitoredBlankPrefix", "1");
        assertNotNull(getsCounter("cache", "monitoredBlankPrefix", "miss"));
    }

    @Test
    @DisplayName("detailed logging runs without error and records latency")
    void detailedLogging() throws Throwable {
        assertEquals("d-1", invoke("monitoredDetailed", "1"));
        assertEquals(1, registry.find("cache.latency")
            .tag("method", CLASS_TAG + ".monitoredDetailed").timer().count());
    }

    @Test
    @DisplayName("a low hit rate raises the hit-rate alert")
    void hitRateAlert() throws Throwable {
        CacheFacade cache = CacheFacade.builder()
            .cacheName("mon-hit").maximumSize(100).recordStats(true).build();
        cacheManager.registerCache("mon-hit", cache);
        // two misses => hit rate 0 which is below the 0.9 threshold
        cache.get("absent-a");
        cache.get("absent-b");

        invoke("monitoredHitAlert", "1");

        Counter alert = registry.find("cache.alerts")
            .tag("method", CLASS_TAG + ".monitoredHitAlert")
            .tag("type", "hit-rate").counter();
        assertNotNull(alert);
        assertEquals(1.0, alert.count());
    }

    @Test
    @DisplayName("a high eviction rate raises the eviction-rate alert")
    void evictionRateAlert() throws Throwable {
        CacheFacade cache = CacheFacade.builder()
            .cacheName("mon-evict").maximumSize(1).recordStats(true).build();
        cacheManager.registerCache("mon-evict", cache);
        for (int i = 0; i < 6; i++) {
            cache.put("k" + i, "v" + i);
        }
        cache.get("some-request"); // ensure requestCount > 0
        await().atMost(2, TimeUnit.SECONDS).until(() -> {
            cache.cleanUp();
            return cache.stats().evictionCount() > 0;
        });

        invoke("monitoredEvictAlert", "1");

        Counter alert = registry.find("cache.alerts")
            .tag("method", CLASS_TAG + ".monitoredEvictAlert")
            .tag("type", "eviction-rate").counter();
        assertNotNull(alert);
        assertEquals(1.0, alert.count());
    }

    @Test
    @DisplayName("no alerts are raised when the cache has no requests yet")
    void noAlertsWithoutRequests() throws Throwable {
        // freshly created 'mon' cache with zero requests => checkAlerts returns early
        invoke("monitored", "1");
        assertNull(registry.find("cache.alerts")
            .tag("method", CLASS_TAG + ".monitored").counter());
    }

    @Test
    @DisplayName("the aspect tolerates a null metrics binder")
    void nullMetricsBinder() throws Throwable {
        CacheMonitorAspect noBinder = new CacheMonitorAspect(cacheManager, keyGenerator, registry, null);
        TestJoinPoint joinPoint = new TestJoinPoint(sample, "monitored", new Object[]{"1"});
        assertEquals("v-1", noBinder.aroundCacheMonitor(joinPoint,
            joinPoint.method().getAnnotation(CacheMonitor.class)));
    }

    @Test
    @DisplayName("a monitored cache is bound into the metrics binder")
    void monitoredCacheIsBound() throws Throwable {
        invoke("monitored", "1");
        assertTrue(metricsBinder.getBoundCacheCount() >= 1);
        assertNotNull(registry.find("adhar.cache.size").tag("cache", "mon").gauge());
    }
}
