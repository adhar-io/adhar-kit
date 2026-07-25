package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.annotation.AiCache;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiCacheAspect} caching behaviour, now backed by Caffeine
 * (TTL + size eviction) instead of the previous unbounded {@code ConcurrentHashMap}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiCacheAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private AiCacheAspect aspect;
    private Method method;

    @SuppressWarnings("unused")
    static class Sample {
        public String compute(String in) { return null; }
        public String computeTwo(String first, String second) { return null; }
    }

    @BeforeEach
    void setUp() throws Exception {
        aspect = new AiCacheAspect();
        method = Sample.class.getMethod("compute", String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getParameterNames()).thenReturn(new String[]{"in"});
    }

    private AiCache annotation(int ttl, int maxEntries, boolean includeAll, String[] includeParams) {
        return annotation(ttl, maxEntries, includeAll, includeParams, new String[]{});
    }

    private AiCache annotation(int ttl, int maxEntries, boolean includeAll, String[] includeParams,
                                String[] excludeParams) {
        AiCache ann = mock(AiCache.class);
        when(ann.ttl()).thenReturn(ttl);
        when(ann.maxEntries()).thenReturn(maxEntries);
        when(ann.cacheName()).thenReturn("ai-cache");
        when(ann.includeAllParams()).thenReturn(includeAll);
        when(ann.includeParams()).thenReturn(includeParams);
        when(ann.excludeParams()).thenReturn(excludeParams);
        return ann;
    }

    @Test
    void cachesResultAndServesFromCacheOnHit() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"input"});
        when(joinPoint.proceed()).thenReturn("computed");
        AiCache ann = annotation(3600, 1000, true, new String[]{});

        Object first = aspect.processCacheAnnotation(joinPoint, ann);
        Object second = aspect.processCacheAnnotation(joinPoint, ann);

        assertThat(first).isEqualTo("computed");
        assertThat(second).isEqualTo("computed");
        // proceed only invoked once: second call served from cache
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void expiredEntryTriggersRecompute() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"input"});
        when(joinPoint.proceed()).thenReturn("v1", "v2");
        AiCache ann = annotation(0, 1000, true, new String[]{}); // ttl 0 -> Caffeine expires immediately on write

        Object first = aspect.processCacheAnnotation(joinPoint, ann);
        Object second = aspect.processCacheAnnotation(joinPoint, ann);

        assertThat(first).isEqualTo("v1");
        assertThat(second).isEqualTo("v2");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void usesIncludeParamsForKeyGeneration() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"keyparam"});
        when(joinPoint.proceed()).thenReturn("result");
        AiCache ann = annotation(3600, 1000, false, new String[]{"0"});

        Object result = aspect.processCacheAnnotation(joinPoint, ann);

        assertThat(result).isEqualTo("result");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void includeParamsResolvesByParameterNameNotJustIndexZero() throws Throwable {
        Method twoArgMethod = Sample.class.getMethod("computeTwo", String.class, String.class);
        when(signature.getMethod()).thenReturn(twoArgMethod);
        when(signature.getParameterNames()).thenReturn(new String[]{"first", "second"});
        // Only "second" is part of the cache key: varying "first" alone must not
        // produce cache misses, proving the key generation no longer hard-codes
        // parameter index 0 (the original bug).
        AiCache ann = annotation(3600, 1000, false, new String[]{"second"});
        when(joinPoint.proceed()).thenReturn("cached-result");

        when(joinPoint.getArgs()).thenReturn(new Object[]{"alpha", "shared"});
        Object first = aspect.processCacheAnnotation(joinPoint, ann);

        when(joinPoint.getArgs()).thenReturn(new Object[]{"beta", "shared"});
        Object second = aspect.processCacheAnnotation(joinPoint, ann);

        assertThat(first).isEqualTo("cached-result");
        assertThat(second).isEqualTo("cached-result");
        // Both calls share the same "second" argument, so only one proceed() call
        // should have occurred - the varying "first" argument was correctly ignored.
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void includeAllParamsDistinguishesDifferentMultiParamCombinations() throws Throwable {
        Method twoArgMethod = Sample.class.getMethod("computeTwo", String.class, String.class);
        when(signature.getMethod()).thenReturn(twoArgMethod);
        when(signature.getParameterNames()).thenReturn(new String[]{"first", "second"});
        AiCache ann = annotation(3600, 1000, true, new String[]{});
        when(joinPoint.proceed()).thenReturn("r1", "r2");

        when(joinPoint.getArgs()).thenReturn(new Object[]{"a", "x"});
        Object first = aspect.processCacheAnnotation(joinPoint, ann);

        when(joinPoint.getArgs()).thenReturn(new Object[]{"a", "y"});
        Object second = aspect.processCacheAnnotation(joinPoint, ann);

        assertThat(first).isEqualTo("r1");
        assertThat(second).isEqualTo("r2");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void excludeParamsIgnoresConfiguredArgumentsWhenIncludingAllParams() throws Throwable {
        Method twoArgMethod = Sample.class.getMethod("computeTwo", String.class, String.class);
        when(signature.getMethod()).thenReturn(twoArgMethod);
        when(signature.getParameterNames()).thenReturn(new String[]{"first", "second"});
        // "first" (e.g. a per-request trace id) is excluded from the key, so two
        // calls that only differ in "first" should still hit the cache.
        AiCache ann = annotation(3600, 1000, true, new String[]{}, new String[]{"first"});
        when(joinPoint.proceed()).thenReturn("cached-result");

        when(joinPoint.getArgs()).thenReturn(new Object[]{"trace-1", "same"});
        Object first = aspect.processCacheAnnotation(joinPoint, ann);

        when(joinPoint.getArgs()).thenReturn(new Object[]{"trace-2", "same"});
        Object second = aspect.processCacheAnnotation(joinPoint, ann);

        assertThat(first).isEqualTo("cached-result");
        assertThat(second).isEqualTo("cached-result");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    void nullResultIsNotCached() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});
        when(joinPoint.proceed()).thenReturn(null);
        AiCache ann = annotation(3600, 1000, true, new String[]{});

        aspect.processCacheAnnotation(joinPoint, ann);
        aspect.processCacheAnnotation(joinPoint, ann);

        // null never cached, so proceed runs each time
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    void cleansUpWhenExceedingMaxEntries() throws Throwable {
        AiCache ann = annotation(3600, 1, true, new String[]{});
        when(joinPoint.proceed()).thenReturn("r1", "r2");

        when(joinPoint.getArgs()).thenReturn(new Object[]{"a"});
        aspect.processCacheAnnotation(joinPoint, ann);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"b"});
        aspect.processCacheAnnotation(joinPoint, ann);

        // Caffeine's size-based eviction (maximumSize=1) evicts the older entry
        // ("a") once a second distinct key ("b") is written, so recomputing "a"
        // proceeds again.
        when(joinPoint.getArgs()).thenReturn(new Object[]{"a"});
        when(joinPoint.proceed()).thenReturn("r1-again");
        Object recomputed = aspect.processCacheAnnotation(joinPoint, ann);
        assertThat(recomputed).isEqualTo("r1-again");
    }

    @Test
    void distinctCacheNamesGetIndependentCaches() throws Throwable {
        when(joinPoint.getArgs()).thenReturn(new Object[]{"same-input"});
        when(joinPoint.proceed()).thenReturn("from-cache-a", "from-cache-b");

        AiCache cacheA = annotation(3600, 1000, true, new String[]{});
        when(cacheA.cacheName()).thenReturn("cache-a");
        AiCache cacheB = annotation(3600, 1000, true, new String[]{});
        when(cacheB.cacheName()).thenReturn("cache-b");

        Object resultA = aspect.processCacheAnnotation(joinPoint, cacheA);
        Object resultB = aspect.processCacheAnnotation(joinPoint, cacheB);

        assertThat(resultA).isEqualTo("from-cache-a");
        assertThat(resultB).isEqualTo("from-cache-b");
        verify(joinPoint, times(2)).proceed();
    }
}
