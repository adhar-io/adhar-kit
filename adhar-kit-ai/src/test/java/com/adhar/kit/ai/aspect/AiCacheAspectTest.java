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
 * Unit tests for {@link AiCacheAspect} caching behaviour.
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
    }

    @BeforeEach
    void setUp() throws Exception {
        aspect = new AiCacheAspect();
        method = Sample.class.getMethod("compute", String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
    }

    private AiCache annotation(int ttl, int maxEntries, boolean includeAll, String[] includeParams) {
        AiCache ann = mock(AiCache.class);
        when(ann.ttl()).thenReturn(ttl);
        when(ann.maxEntries()).thenReturn(maxEntries);
        when(ann.cacheName()).thenReturn("ai-cache");
        when(ann.includeAllParams()).thenReturn(includeAll);
        when(ann.includeParams()).thenReturn(includeParams);
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
        AiCache ann = annotation(0, 1000, true, new String[]{}); // ttl 0 -> always expired

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

        // The oldest entry should have been evicted; recomputing "a" proceeds again
        when(joinPoint.getArgs()).thenReturn(new Object[]{"a"});
        when(joinPoint.proceed()).thenReturn("r1-again");
        Object recomputed = aspect.processCacheAnnotation(joinPoint, ann);
        assertThat(recomputed).isEqualTo("r1-again");
    }
}
