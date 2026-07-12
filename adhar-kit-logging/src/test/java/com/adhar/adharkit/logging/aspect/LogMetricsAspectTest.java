package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LogMetricsAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogMetricsAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private LogMetricsAspect aspect;

    @SuppressWarnings("unused")
    static class SampleTarget {
        public String perform(String input) {
            return "ok";
        }
    }

    @LogMetrics("class-metric")
    @SuppressWarnings("unused")
    static class ClassAnnotated {
        public void run() {
        }
    }

    @SuppressWarnings("unused")
    static class MethodAnnotated {
        @LogMetrics
        public void doWork() {
        }
    }

    private Method method(Class<?> c, String name, Class<?>... params) throws Exception {
        return c.getDeclaredMethod(name, params);
    }

    private LogMetrics metrics(String value, String metricName, Level level, boolean execTime,
                               boolean includeArgs, boolean includeResult, String[] tags, double sampleRate) {
        LogMetrics m = mock(LogMetrics.class);
        when(m.value()).thenReturn(value);
        when(m.metricName()).thenReturn(metricName);
        when(m.level()).thenReturn(level);
        when(m.includeExecutionTime()).thenReturn(execTime);
        when(m.includeArgs()).thenReturn(includeArgs);
        when(m.includeResult()).thenReturn(includeResult);
        when(m.tags()).thenReturn(tags);
        when(m.sampleRate()).thenReturn(sampleRate);
        return m;
    }

    @BeforeEach
    void setUp() {
        aspect = new LogMetricsAspect(new ObjectMapper());
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void logsMetricsOnSuccessWithCollectionResult() throws Throwable {
        MDC.put("correlationId", "cid");
        MDC.put("userId", "u1");
        MDC.put("tenantId", "t1");
        MDC.put("businessContext", "checkout");

        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"str", 7, false, List.of(1, 2, 3)});
        when(joinPoint.proceed()).thenReturn(List.of("a", "b"));

        LogMetrics ann = metrics("", "", Level.INFO, true, true, true,
                new String[]{"tag1"}, 1.0);

        assertThat(aspect.logMetrics(joinPoint, ann)).isEqualTo(List.of("a", "b"));
    }

    @Test
    void sanitizesMapArrayAndObjectResults() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{Map.of("k", "v"), new int[]{1, 2}, new SampleTarget()});
        when(joinPoint.proceed()).thenReturn(new SampleTarget());

        LogMetrics ann = metrics("op", "custom-metric", Level.DEBUG, false, true, true,
                new String[]{}, 1.0);

        assertThat(aspect.logMetrics(joinPoint, ann)).isInstanceOf(SampleTarget.class);
    }

    @Test
    void logsMetricsOnException() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"in"});
        RuntimeException ex = new RuntimeException("fail");
        when(joinPoint.proceed()).thenThrow(ex);

        LogMetrics ann = metrics("", "", Level.WARN, true, false, false, new String[]{}, 1.0);
        assertThatThrownBy(() -> aspect.logMetrics(joinPoint, ann)).isSameAs(ex);
    }

    @Test
    void skipsWhenSampleRateZero() throws Throwable {
        when(joinPoint.proceed()).thenReturn("done");
        LogMetrics ann = metrics("", "", Level.INFO, true, false, false, new String[]{}, 0.0);

        assertThat(aspect.logMetrics(joinPoint, ann)).isEqualTo("done");
        verify(signature, never()).getMethod();
    }

    @Test
    void resolvesMethodAnnotationWhenNull() throws Throwable {
        Method m = method(MethodAnnotated.class, "doWork");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);
        assertThat(aspect.logMetrics(joinPoint, null)).isNull();
    }

    @Test
    void resolvesClassAnnotationWhenNull() throws Throwable {
        Method m = method(ClassAnnotated.class, "run");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);
        assertThat(aspect.logMetrics(joinPoint, null)).isNull();
    }

    @Test
    void proceedsWhenNoAnnotationFound() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.proceed()).thenReturn("plain");
        assertThat(aspect.logMetrics(joinPoint, null)).isEqualTo("plain");
    }

    @Test
    void tracePathWithNullResult() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);

        LogMetrics ann = metrics("", "", Level.TRACE, true, true, true, new String[]{}, 1.0);
        assertThat(aspect.logMetrics(joinPoint, ann)).isNull();
    }

    @Test
    void errorLevelPath() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("x");

        LogMetrics ann = metrics("", "", Level.ERROR, false, false, false, new String[]{}, 1.0);
        assertThat(aspect.logMetrics(joinPoint, ann)).isEqualTo("x");
    }
}
