package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.annotation.MultiLevelCache;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import com.adhar.adharkit.cache.multilevel.InMemorySecondLevelCache;
import com.adhar.adharkit.cache.multilevel.MultiLevelCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link MultiLevelCacheAspect} with a real annotated sample class.
 */
@DisplayName("MultiLevelCacheAspect Tests")
class MultiLevelCacheAspectTest {

    private CacheManager cacheManager;
    private InMemorySecondLevelCache l2;
    private MultiLevelCacheAspect aspect;
    private ProductService service;

    /**
     * Real annotated sample class.
     */
    static class ProductService {
        final AtomicInteger invocations = new AtomicInteger();

        @MultiLevelCache(l1Cache = "mlt-l1", l2Cache = "mlt-l2", key = "#productId")
        public String findProduct(String productId) {
            invocations.incrementAndGet();
            return "product-" + productId;
        }

        @MultiLevelCache(l1Cache = "mlt-l1", l2Cache = "mlt-l2", key = "#productId", writeThrough = false)
        public String findProductNoWriteThrough(String productId) {
            invocations.incrementAndGet();
            return "nwt-" + productId;
        }

        @MultiLevelCache(l1Cache = "mlt-l1", l2Cache = "mlt-l2", key = "#productId")
        public String findProductFailing(String productId) {
            invocations.incrementAndGet();
            throw new IllegalArgumentException("mlc-boom");
        }
    }

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        l2 = new InMemorySecondLevelCache();
        aspect = new MultiLevelCacheAspect(
            new MultiLevelCacheService(cacheManager, l2), new CacheKeyGenerator());
        service = new ProductService();
        if (cacheManager.getCache("mlt-l1") != null) {
            cacheManager.getCache("mlt-l1").clear();
        }
        l2.clear("mlt-l2");
    }

    private Object invoke(String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(service, methodName, args);
        return aspect.aroundMultiLevelCache(joinPoint,
            joinPoint.method().getAnnotation(MultiLevelCache.class));
    }

    @Test
    @DisplayName("miss loads once and stores in both levels; hit skips proceed()")
    void missThenL1Hit() throws Throwable {
        assertEquals("product-p1", invoke("findProduct", "p1"));
        assertEquals(1, service.invocations.get());
        assertEquals("product-p1", cacheManager.getCache("mlt-l1").get("p1"));
        assertEquals("product-p1", l2.get("mlt-l2", "p1"));

        TestJoinPoint second = new TestJoinPoint(service, "findProduct", "p1");
        assertEquals("product-p1", aspect.aroundMultiLevelCache(second,
            second.method().getAnnotation(MultiLevelCache.class)));
        assertEquals(0, second.proceedCount.get(), "L1 hit must not invoke the method");
    }

    @Test
    @DisplayName("L1 eviction falls back to L2 without re-invoking the method")
    void l2FallbackAfterL1Eviction() throws Throwable {
        invoke("findProduct", "p2");
        cacheManager.getCache("mlt-l1").evict("p2");

        assertEquals("product-p2", invoke("findProduct", "p2"));
        assertEquals(1, service.invocations.get(), "L2 hit must serve without reload");
        assertEquals("product-p2", cacheManager.getCache("mlt-l1").get("p2"),
            "value must be promoted back into L1");
    }

    @Test
    @DisplayName("writeThrough=false keeps loaded values out of L2")
    void noWriteThrough() throws Throwable {
        assertEquals("nwt-p3", invoke("findProductNoWriteThrough", "p3"));
        assertEquals("nwt-p3", cacheManager.getCache("mlt-l1").get("p3"));
        assertNull(l2.get("mlt-l2", "p3"));
    }

    @Test
    @DisplayName("method exceptions are unwrapped and propagated")
    void exceptionPropagates() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> invoke("findProductFailing", "p4"));
        assertEquals("mlc-boom", ex.getMessage());
        assertNull(cacheManager.getCache("mlt-l1").get("p4"));
    }
}
