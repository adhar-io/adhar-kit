package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogExecutionTime;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LogExecutionTimeAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogExecutionTimeAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private LogExecutionTimeAspect aspect;

    @SuppressWarnings("unused")
    static class SampleTarget {
        public String perform(String input) {
            return "ok";
        }
    }

    @LogExecutionTime("class-op")
    @SuppressWarnings("unused")
    static class ClassAnnotated {
        public void run() {
        }
    }

    @SuppressWarnings("unused")
    static class MethodAnnotated {
        @LogExecutionTime
        public void doWork() {
        }
    }

    private Method method(Class<?> c, String name, Class<?>... params) throws Exception {
        return c.getDeclaredMethod(name, params);
    }

    private LogExecutionTime annotation(String value, Level level, long thresholdMs,
                                        boolean includeArgs, double sampleRate) {
        LogExecutionTime a = mock(LogExecutionTime.class);
        when(a.value()).thenReturn(value);
        when(a.level()).thenReturn(level);
        when(a.thresholdMs()).thenReturn(thresholdMs);
        when(a.includeArgs()).thenReturn(includeArgs);
        when(a.sampleRate()).thenReturn(sampleRate);
        return a;
    }

    @BeforeEach
    void setUp() {
        aspect = new LogExecutionTimeAspect(new ObjectMapper());
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void logsExecutionTimeWhenThresholdMet() throws Throwable {
        MDC.put("correlationId", "cid");
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"in"});
        when(joinPoint.proceed()).thenReturn("ok");

        // threshold 0 means always log
        LogExecutionTime ann = annotation("", Level.INFO, 0, true, 1.0);
        assertThat(aspect.logExecutionTime(joinPoint, ann)).isEqualTo("ok");
    }

    @Test
    void doesNotLogWhenBelowThreshold() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"in"});
        when(joinPoint.proceed()).thenReturn("fast");

        // huge threshold => no logging branch
        LogExecutionTime ann = annotation("op", Level.DEBUG, 1_000_000, false, 1.0);
        assertThat(aspect.logExecutionTime(joinPoint, ann)).isEqualTo("fast");
    }

    @Test
    void logsExecutionTimeWithExceptionWhenThresholdMet() throws Throwable {
        MDC.put("correlationId", "cid");
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"in"});
        RuntimeException ex = new RuntimeException("fail");
        when(joinPoint.proceed()).thenThrow(ex);

        LogExecutionTime ann = annotation("", Level.WARN, 0, true, 1.0);
        assertThatThrownBy(() -> aspect.logExecutionTime(joinPoint, ann)).isSameAs(ex);
    }

    @Test
    void exceptionBelowThresholdNotLogged() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"in"});
        RuntimeException ex = new RuntimeException("fail");
        when(joinPoint.proceed()).thenThrow(ex);

        LogExecutionTime ann = annotation("", Level.ERROR, 1_000_000, false, 1.0);
        assertThatThrownBy(() -> aspect.logExecutionTime(joinPoint, ann)).isSameAs(ex);
    }

    @Test
    void skipsWhenSampleRateZero() throws Throwable {
        when(joinPoint.proceed()).thenReturn("done");
        LogExecutionTime ann = annotation("", Level.INFO, 0, false, 0.0);
        assertThat(aspect.logExecutionTime(joinPoint, ann)).isEqualTo("done");
        verify(signature, never()).getMethod();
    }

    @Test
    void resolvesMethodAnnotationWhenNull() throws Throwable {
        Method m = method(MethodAnnotated.class, "doWork");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);
        assertThat(aspect.logExecutionTime(joinPoint, null)).isNull();
    }

    @Test
    void resolvesClassAnnotationWhenNull() throws Throwable {
        Method m = method(ClassAnnotated.class, "run");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);
        assertThat(aspect.logExecutionTime(joinPoint, null)).isNull();
    }

    @Test
    void proceedsWhenNoAnnotationFound() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.proceed()).thenReturn("plain");
        assertThat(aspect.logExecutionTime(joinPoint, null)).isEqualTo("plain");
    }

    @Test
    void traceLevelPath() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("x");
        LogExecutionTime ann = annotation("", Level.TRACE, 0, false, 1.0);
        assertThat(aspect.logExecutionTime(joinPoint, ann)).isEqualTo("x");
    }
}
