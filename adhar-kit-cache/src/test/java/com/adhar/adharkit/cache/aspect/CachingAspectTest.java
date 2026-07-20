package com.adhar.adharkit.cache.aspect;

import com.adhar.adharkit.cache.CacheFacade;
import com.adhar.adharkit.cache.annotation.CacheEvict;
import com.adhar.adharkit.cache.annotation.CachePut;
import com.adhar.adharkit.cache.annotation.Cacheable;
import com.adhar.adharkit.cache.key.CacheKeyGenerator;
import com.adhar.adharkit.cache.manager.CacheManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CachingAspect} using a real annotated sample class and
 * a {@link TestJoinPoint} double.
 */
@DisplayName("CachingAspect Tests")
class CachingAspectTest {

    private CacheManager cacheManager;
    private CachingAspect aspect;
    private SampleService service;

    /**
     * Real annotated sample class exercised through the aspect.
     */
    static class SampleService {
        final AtomicInteger invocations = new AtomicInteger();
        volatile String nextResult = "value";

        @Cacheable(cacheName = "ct-users", key = "#userId")
        public String findUser(String userId) {
            invocations.incrementAndGet();
            return "user-" + userId;
        }

        @Cacheable(key = "#id")
        public String findByMethodNameCache(String id) {
            invocations.incrementAndGet();
            return "m-" + id;
        }

        @Cacheable(cacheName = "ct-default-key")
        public String findDefaultKey(String id) {
            invocations.incrementAndGet();
            return "dk-" + id;
        }

        @Cacheable(cacheName = "ct-ttl", key = "#id", ttl = 150, ttlUnit = TimeUnit.MILLISECONDS)
        public String findWithTtl(String id) {
            invocations.incrementAndGet();
            return "ttl-" + id + "-" + invocations.get();
        }

        @Cacheable(cacheName = "ct-cond", key = "#id", condition = "#id.startsWith('P')")
        public String findConditional(String id) {
            invocations.incrementAndGet();
            return "c-" + id;
        }

        @Cacheable(cacheName = "ct-result-cond", key = "#id", condition = "#result != null && #result.length() > 5")
        public String findResultConditional(String id) {
            invocations.incrementAndGet();
            return nextResult;
        }

        @Cacheable(cacheName = "ct-unless", key = "#id", unless = "#result.startsWith('skip')")
        public String findUnless(String id) {
            invocations.incrementAndGet();
            return nextResult;
        }

        @Cacheable(cacheName = "ct-null", key = "#id")
        public String findNull(String id) {
            invocations.incrementAndGet();
            return null;
        }

        @Cacheable(cacheName = "ct-sync", key = "#id", sync = true)
        public String findSync(String id) {
            invocations.incrementAndGet();
            return "sync-" + id;
        }

        @Cacheable(cacheName = "ct-sync-ex", key = "#id", sync = true)
        public String findSyncFailing(String id) {
            invocations.incrementAndGet();
            throw new IllegalStateException("sync-boom");
        }

        @Cacheable(cacheName = "ct-users", key = "#userId")
        public String findUserFailing(String userId) {
            invocations.incrementAndGet();
            throw new IllegalStateException("boom");
        }

        @CachePut(cacheName = "ct-users", key = "#userId")
        public String saveUser(String userId) {
            invocations.incrementAndGet();
            return "saved-" + userId;
        }

        @CachePut(cacheName = "ct-put-unless", key = "#id", unless = "#result.startsWith('tmp')")
        public String savePutUnless(String id) {
            invocations.incrementAndGet();
            return nextResult;
        }

        @CachePut(cacheName = "ct-put-cond", key = "#id", condition = "#id.startsWith('OK')")
        public String savePutConditional(String id) {
            invocations.incrementAndGet();
            return "pc-" + id;
        }

        @CachePut(cacheName = "ct-put-null", key = "#id")
        public String savePutNull(String id) {
            invocations.incrementAndGet();
            return null;
        }

        @CacheEvict(cacheName = "ct-users", key = "#userId")
        public void deleteUser(String userId) {
            invocations.incrementAndGet();
        }

        @CacheEvict(cacheName = "ct-users", allEntries = true)
        public void clearUsers() {
            invocations.incrementAndGet();
        }

        @CacheEvict(cacheName = "ct-users", key = "#userId", beforeInvocation = true)
        public void deleteUserFailing(String userId) {
            invocations.incrementAndGet();
            throw new IllegalStateException("delete-boom");
        }

        @CacheEvict(cacheName = "ct-users", key = "#userId", condition = "#userId.startsWith('X')")
        public void deleteUserConditional(String userId) {
            invocations.incrementAndGet();
        }

        @CacheEvict(cacheName = "ct-missing-cache", key = "#userId")
        public void deleteFromMissingCache(String userId) {
            invocations.incrementAndGet();
        }
    }

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        aspect = new CachingAspect(cacheManager, new CacheKeyGenerator());
        service = new SampleService();
        // isolate shared singleton state between tests
        for (String name : new String[]{"ct-users", "ct-ttl", "ct-cond", "ct-result-cond",
            "ct-unless", "ct-null", "ct-sync", "ct-sync-ex", "ct-put-unless", "ct-put-cond",
            "ct-put-null", "ct-default-key", "findByMethodNameCache"}) {
            CacheFacade cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    private Object invokeCacheable(String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(service, methodName, args);
        return aspect.aroundCacheable(joinPoint, joinPoint.method().getAnnotation(Cacheable.class));
    }

    private Object invokeCachePut(String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(service, methodName, args);
        return aspect.aroundCachePut(joinPoint, joinPoint.method().getAnnotation(CachePut.class));
    }

    private Object invokeCacheEvict(String methodName, Object... args) throws Throwable {
        TestJoinPoint joinPoint = new TestJoinPoint(service, methodName, args);
        return aspect.aroundCacheEvict(joinPoint, joinPoint.method().getAnnotation(CacheEvict.class));
    }

    // ==================== @Cacheable ====================

    @Test
    @DisplayName("@Cacheable caches the result and a hit skips proceed()")
    void cacheableHitSkipsProceed() throws Throwable {
        assertEquals("user-u1", invokeCacheable("findUser", "u1"));
        assertEquals(1, service.invocations.get());

        TestJoinPoint second = new TestJoinPoint(service, "findUser", "u1");
        assertEquals("user-u1",
            aspect.aroundCacheable(second, second.method().getAnnotation(Cacheable.class)));
        assertEquals(0, second.proceedCount.get(), "cache hit must not invoke the method");
        assertEquals(1, service.invocations.get());
        assertEquals("user-u1", cacheManager.getCache("ct-users").get("u1"));
    }

    @Test
    @DisplayName("@Cacheable uses the method name as cache name when blank")
    void cacheableDefaultsCacheNameToMethodName() throws Throwable {
        invokeCacheable("findByMethodNameCache", "a");
        assertNotNull(cacheManager.getCache("findByMethodNameCache"));
        assertEquals("m-a", cacheManager.getCache("findByMethodNameCache").get("a"));
    }

    @Test
    @DisplayName("@Cacheable uses the default key when no key expression given")
    void cacheableDefaultKey() throws Throwable {
        invokeCacheable("findDefaultKey", "z1");
        invokeCacheable("findDefaultKey", "z1");
        assertEquals(1, service.invocations.get());
        invokeCacheable("findDefaultKey", "z2");
        assertEquals(2, service.invocations.get(), "different args must produce a different default key");
    }

    @Test
    @DisplayName("@Cacheable honors ttl/ttlUnit on cache creation")
    void cacheableTtlExpires() throws Throwable {
        invokeCacheable("findWithTtl", "t1");
        invokeCacheable("findWithTtl", "t1");
        assertEquals(1, service.invocations.get());

        await().atMost(2, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS)
            .until(() -> {
                invokeCacheableUnchecked("findWithTtl", "t1");
                return service.invocations.get() >= 2;
            });
    }

    private void invokeCacheableUnchecked(String methodName, Object... args) {
        try {
            invokeCacheable(methodName, args);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    @Test
    @DisplayName("@Cacheable pre-condition false bypasses caching entirely")
    void cacheableConditionVeto() throws Throwable {
        invokeCacheable("findConditional", "STD-1");
        invokeCacheable("findConditional", "STD-1");
        assertEquals(2, service.invocations.get(), "vetoed calls always proceed");

        invokeCacheable("findConditional", "P-1");
        invokeCacheable("findConditional", "P-1");
        assertEquals(3, service.invocations.get(), "matching condition caches the value");
    }

    @Test
    @DisplayName("@Cacheable #result condition stores only when it passes")
    void cacheableResultCondition() throws Throwable {
        service.nextResult = "ab";
        invokeCacheable("findResultConditional", "r1");
        assertNull(cacheManager.getCache("ct-result-cond").get("r1"), "short result must not be cached");

        service.nextResult = "long-enough";
        invokeCacheable("findResultConditional", "r2");
        assertEquals("long-enough", cacheManager.getCache("ct-result-cond").get("r2"));
    }

    @Test
    @DisplayName("@Cacheable unless vetoes storing")
    void cacheableUnless() throws Throwable {
        service.nextResult = "skip-me";
        invokeCacheable("findUnless", "s1");
        assertNull(cacheManager.getCache("ct-unless").get("s1"));

        service.nextResult = "keep-me";
        invokeCacheable("findUnless", "s2");
        assertEquals("keep-me", cacheManager.getCache("ct-unless").get("s2"));
    }

    @Test
    @DisplayName("@Cacheable null results are not cached")
    void cacheableNullNotCached() throws Throwable {
        assertNull(invokeCacheable("findNull", "n1"));
        assertNull(invokeCacheable("findNull", "n1"));
        assertEquals(2, service.invocations.get());
    }

    @Test
    @DisplayName("@Cacheable sync=true computes once and caches")
    void cacheableSync() throws Throwable {
        assertEquals("sync-x", invokeCacheable("findSync", "x"));

        TestJoinPoint second = new TestJoinPoint(service, "findSync", "x");
        assertEquals("sync-x",
            aspect.aroundCacheable(second, second.method().getAnnotation(Cacheable.class)));
        assertEquals(0, second.proceedCount.get());
        assertEquals(1, service.invocations.get());
    }

    @Test
    @DisplayName("@Cacheable sync=true unwraps method exceptions")
    void cacheableSyncPropagatesException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> invokeCacheable("findSyncFailing", "x"));
        assertEquals("sync-boom", ex.getMessage());
    }

    @Test
    @DisplayName("@Cacheable propagates method exceptions without caching")
    void cacheableExceptionPropagates() {
        assertThrows(IllegalStateException.class, () -> invokeCacheable("findUserFailing", "e1"));
        assertNull(cacheManager.getCache("ct-users").get("e1"));
    }

    // ==================== @CachePut ====================

    @Test
    @DisplayName("@CachePut always executes and stores the result")
    void cachePutExecutesAndStores() throws Throwable {
        assertEquals("saved-p1", invokeCachePut("saveUser", "p1"));
        assertEquals("saved-p1", invokeCachePut("saveUser", "p1"));
        assertEquals(2, service.invocations.get(), "@CachePut must always execute the method");
        assertEquals("saved-p1", cacheManager.getCache("ct-users").get("p1"));
    }

    @Test
    @DisplayName("@CachePut updates the value seen by @Cacheable")
    void cachePutUpdatesCacheableView() throws Throwable {
        invokeCacheable("findUser", "u9");
        assertEquals("user-u9", cacheManager.getCache("ct-users").get("u9"));

        invokeCachePut("saveUser", "u9");
        assertEquals("saved-u9", invokeCacheable("findUser", "u9"),
            "@Cacheable must see the @CachePut-updated value");
    }

    @Test
    @DisplayName("@CachePut unless vetoes storing")
    void cachePutUnless() throws Throwable {
        service.nextResult = "tmp-1";
        invokeCachePut("savePutUnless", "k1");
        CacheFacade unlessCache = cacheManager.getCache("ct-put-unless");
        assertTrue(unlessCache == null || unlessCache.get("k1") == null,
            "vetoed put must not store");

        service.nextResult = "final-1";
        invokeCachePut("savePutUnless", "k2");
        assertEquals("final-1", cacheManager.getCache("ct-put-unless").get("k2"));
    }

    @Test
    @DisplayName("@CachePut condition guards storing")
    void cachePutCondition() throws Throwable {
        invokeCachePut("savePutConditional", "NO-1");
        CacheFacade condCache = cacheManager.getCache("ct-put-cond");
        assertTrue(condCache == null || condCache.get("NO-1") == null,
            "failed condition must not store");

        invokeCachePut("savePutConditional", "OK-1");
        assertEquals("pc-OK-1", cacheManager.getCache("ct-put-cond").get("OK-1"));
    }

    @Test
    @DisplayName("@CachePut skips null results")
    void cachePutNullSkipped() throws Throwable {
        assertNull(invokeCachePut("savePutNull", "k"));
        CacheFacade cache = cacheManager.getCache("ct-put-null");
        assertTrue(cache == null || cache.get("k") == null);
    }

    // ==================== @CacheEvict ====================

    @Test
    @DisplayName("@CacheEvict removes a single key after invocation")
    void cacheEvictSingleKey() throws Throwable {
        invokeCacheable("findUser", "d1");
        invokeCacheable("findUser", "d2");

        invokeCacheEvict("deleteUser", "d1");
        assertNull(cacheManager.getCache("ct-users").get("d1"));
        assertEquals("user-d2", cacheManager.getCache("ct-users").get("d2"));
    }

    @Test
    @DisplayName("@CacheEvict allEntries clears the cache")
    void cacheEvictAllEntries() throws Throwable {
        invokeCacheable("findUser", "d1");
        invokeCacheable("findUser", "d2");

        invokeCacheEvict("clearUsers");
        assertEquals(0, cacheManager.getCache("ct-users").size());
    }

    @Test
    @DisplayName("@CacheEvict beforeInvocation evicts even when the method throws")
    void cacheEvictBeforeInvocation() throws Throwable {
        invokeCacheable("findUser", "d1");

        assertThrows(IllegalStateException.class, () -> invokeCacheEvict("deleteUserFailing", "d1"));
        assertNull(cacheManager.getCache("ct-users").get("d1"),
            "beforeInvocation=true must evict before the method runs");
    }

    @Test
    @DisplayName("@CacheEvict condition false skips eviction")
    void cacheEvictConditionVeto() throws Throwable {
        invokeCacheable("findUser", "d1");

        invokeCacheEvict("deleteUserConditional", "d1");
        assertEquals("user-d1", cacheManager.getCache("ct-users").get("d1"),
            "condition '#userId.startsWith(X)' must veto eviction of d1");

        invokeCacheable("findUser", "X-9");
        invokeCacheEvict("deleteUserConditional", "X-9");
        assertNull(cacheManager.getCache("ct-users").get("X-9"));
    }

    @Test
    @DisplayName("@CacheEvict on an unknown cache is a no-op")
    void cacheEvictMissingCache() throws Throwable {
        assertDoesNotThrow(() -> invokeCacheEvict("deleteFromMissingCache", "k"));
        assertEquals(1, service.invocations.get());
    }
}
