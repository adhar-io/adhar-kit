package com.adhar.kit.core.aspect;

import com.adhar.kit.core.annotation.Memoize;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MemoizeAspect Tests")
class MemoizeAspectTest {

    private MemoizeAspect aspect;

    /**
     * Real annotated sample methods; the annotation instances are read via
     * reflection and passed to the aspect exactly as Spring AOP would.
     */
    @SuppressWarnings("unused")
    static class Sample {

        @Memoize
        String compute(String arg) {
            return null;
        }

        @Memoize(value = "short-lived", ttl = 100)
        String shortLived(String arg) {
            return null;
        }

        @Memoize(useAllParams = false)
        String ignoreArgs(String arg) {
            return null;
        }

        @Memoize
        String other(String arg) {
            return null;
        }
    }

    @BeforeEach
    void setUp() {
        aspect = new MemoizeAspect();
    }

    private static Memoize annotationOf(String methodName) throws Exception {
        return method(methodName).getAnnotation(Memoize.class);
    }

    private static Method method(String methodName) throws Exception {
        return Sample.class.getDeclaredMethod(methodName, String.class);
    }

    private static ProceedingJoinPoint mockJoinPoint(String methodName, Object... args) throws Exception {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method(methodName));
        when(signature.toShortString()).thenReturn("Sample." + methodName + "(..)");
        when(joinPoint.getArgs()).thenReturn(args);
        return joinPoint;
    }

    @Test
    @DisplayName("caches the result for identical arguments")
    void cachesSameArguments() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("compute", "a");
        when(joinPoint.proceed()).thenReturn("computed");

        Object first = aspect.applyMemoize(joinPoint, annotationOf("compute"));
        Object second = aspect.applyMemoize(joinPoint, annotationOf("compute"));

        assertThat(first).isEqualTo("computed");
        assertThat(second).isEqualTo("computed");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("recomputes for different arguments")
    void recomputesForDifferentArguments() throws Throwable {
        ProceedingJoinPoint first = mockJoinPoint("compute", "a");
        ProceedingJoinPoint second = mockJoinPoint("compute", "b");
        when(first.proceed()).thenReturn("value-a");
        when(second.proceed()).thenReturn("value-b");

        assertThat(aspect.applyMemoize(first, annotationOf("compute"))).isEqualTo("value-a");
        assertThat(aspect.applyMemoize(second, annotationOf("compute"))).isEqualTo("value-b");

        verify(first, times(1)).proceed();
        verify(second, times(1)).proceed();
    }

    @Test
    @DisplayName("isolates entries of different methods sharing the default cache")
    void isolatesDifferentMethods() throws Throwable {
        ProceedingJoinPoint computeJp = mockJoinPoint("compute", "a");
        ProceedingJoinPoint otherJp = mockJoinPoint("other", "a");
        when(computeJp.proceed()).thenReturn("from-compute");
        when(otherJp.proceed()).thenReturn("from-other");

        assertThat(aspect.applyMemoize(computeJp, annotationOf("compute"))).isEqualTo("from-compute");
        assertThat(aspect.applyMemoize(otherJp, annotationOf("other"))).isEqualTo("from-other");
    }

    @Test
    @DisplayName("honors the ttl attribute (expired entries are recomputed)")
    void honorsTtl() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("shortLived", "a");
        when(joinPoint.proceed()).thenReturn("v1", "v2");

        Object first = aspect.applyMemoize(joinPoint, annotationOf("shortLived"));
        Thread.sleep(150);
        Object second = aspect.applyMemoize(joinPoint, annotationOf("shortLived"));

        assertThat(first).isEqualTo("v1");
        assertThat(second).isEqualTo("v2");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    @DisplayName("within the ttl the cached value is served")
    void servesCachedValueWithinTtl() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("shortLived", "a");
        when(joinPoint.proceed()).thenReturn("v1");

        Object first = aspect.applyMemoize(joinPoint, annotationOf("shortLived"));
        Object second = aspect.applyMemoize(joinPoint, annotationOf("shortLived"));

        assertThat(first).isEqualTo("v1");
        assertThat(second).isEqualTo("v1");
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("useAllParams=false ignores arguments when building the key")
    void ignoresArgumentsWhenConfigured() throws Throwable {
        ProceedingJoinPoint first = mockJoinPoint("ignoreArgs", "a");
        ProceedingJoinPoint second = mockJoinPoint("ignoreArgs", "b");
        when(first.proceed()).thenReturn("shared");
        when(second.proceed()).thenReturn("never-used");

        assertThat(aspect.applyMemoize(first, annotationOf("ignoreArgs"))).isEqualTo("shared");
        assertThat(aspect.applyMemoize(second, annotationOf("ignoreArgs"))).isEqualTo("shared");

        verify(first, times(1)).proceed();
        verify(second, never()).proceed();
    }

    @Test
    @DisplayName("propagates exceptions and does not cache failures")
    void propagatesExceptionsWithoutCaching() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("compute", "a");
        when(joinPoint.proceed())
            .thenThrow(new IllegalStateException("first failure"))
            .thenReturn("recovered");

        assertThatThrownBy(() -> aspect.applyMemoize(joinPoint, annotationOf("compute")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("first failure");

        assertThat(aspect.applyMemoize(joinPoint, annotationOf("compute"))).isEqualTo("recovered");
        verify(joinPoint, times(2)).proceed();
    }

    @Test
    @DisplayName("propagates checked exceptions unwrapped")
    void propagatesCheckedExceptions() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("compute", "a");
        when(joinPoint.proceed()).thenThrow(new IOException("io failure"));

        assertThatThrownBy(() -> aspect.applyMemoize(joinPoint, annotationOf("compute")))
            .isInstanceOf(IOException.class)
            .hasMessage("io failure");
    }

    @Test
    @DisplayName("clearAll empties every cache so values are recomputed")
    void clearAllForcesRecompute() throws Throwable {
        ProceedingJoinPoint joinPoint = mockJoinPoint("compute", "a");
        when(joinPoint.proceed()).thenReturn("v1", "v2");

        assertThat(aspect.applyMemoize(joinPoint, annotationOf("compute"))).isEqualTo("v1");
        aspect.clearAll();
        assertThat(aspect.applyMemoize(joinPoint, annotationOf("compute"))).isEqualTo("v2");

        verify(joinPoint, times(2)).proceed();
    }
}
