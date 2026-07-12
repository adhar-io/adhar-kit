package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.Loggable;
import com.adhar.adharkit.logging.annotation.Sensitive;
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
 * Unit tests for {@link LoggableAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoggableAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private LoggableAspect aspect;

    @SuppressWarnings("unused")
    static class SampleTarget {
        public String greet(String name) {
            return "hi-" + name;
        }

        public void login(@Sensitive(showFirst = 2, showLast = 2) String password, String user) {
            // no-op
        }

        public void secret(@Sensitive String token) {
            // no-op
        }
    }

    @Loggable
    @SuppressWarnings("unused")
    static class ClassAnnotated {
        public void run() {
            // no-op
        }
    }

    @SuppressWarnings("unused")
    static class MethodAnnotated {
        @Loggable("method-op")
        public void doWork() {
            // no-op
        }
    }

    private Method method(Class<?> c, String name, Class<?>... params) throws Exception {
        return c.getDeclaredMethod(name, params);
    }

    private Loggable loggable(String value, Level level, boolean logArgs, boolean logResult,
                              String[] maskFields, double sampleRate) {
        Loggable l = mock(Loggable.class);
        when(l.value()).thenReturn(value);
        when(l.level()).thenReturn(level);
        when(l.logArgs()).thenReturn(logArgs);
        when(l.logResult()).thenReturn(logResult);
        when(l.maskFields()).thenReturn(maskFields);
        when(l.sampleRate()).thenReturn(sampleRate);
        return l;
    }

    @BeforeEach
    void setUp() {
        aspect = new LoggableAspect(new ObjectMapper());
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void logsEntryAndExitOnSuccess() throws Throwable {
        Method m = method(SampleTarget.class, "greet", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"bob"});
        when(joinPoint.proceed()).thenReturn("hi-bob");

        Loggable l = loggable("", Level.INFO, true, true, new String[]{}, 1.0);

        Object result = aspect.logExecution(joinPoint, l);

        assertThat(result).isEqualTo("hi-bob");
        verify(joinPoint).proceed();
        // MDC should be cleaned up after execution
        assertThat(MDC.get("operation")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void restoresPreviousMdcValues() throws Throwable {
        MDC.put("operation", "outer-op");
        MDC.put("correlationId", "outer-cid");
        Method m = method(SampleTarget.class, "greet", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"x"});
        when(joinPoint.proceed()).thenReturn("hi-x");

        Loggable l = loggable("custom-op", Level.DEBUG, false, false, new String[]{}, 1.0);
        aspect.logExecution(joinPoint, l);

        assertThat(MDC.get("operation")).isEqualTo("outer-op");
        assertThat(MDC.get("correlationId")).isEqualTo("outer-cid");
    }

    @Test
    void skipsLoggingWhenSampleRateZero() throws Throwable {
        when(joinPoint.proceed()).thenReturn("done");
        Loggable l = loggable("", Level.INFO, false, false, new String[]{}, 0.0);

        Object result = aspect.logExecution(joinPoint, l);

        assertThat(result).isEqualTo("done");
        // signature.getMethod should never be consulted when skipping
        verify(signature, never()).getMethod();
    }

    @Test
    void logsExceptionAndRethrows() throws Throwable {
        Method m = method(SampleTarget.class, "greet", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"y"});
        RuntimeException boom = new IllegalStateException("boom");
        when(joinPoint.proceed()).thenThrow(boom);

        Loggable l = loggable("", Level.WARN, true, false, new String[]{}, 1.0);

        assertThatThrownBy(() -> aspect.logExecution(joinPoint, l))
                .isSameAs(boom);
    }

    @Test
    void masksSensitiveParameterWithPartialReveal() throws Throwable {
        Method m = method(SampleTarget.class, "login", String.class, String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"password123", "john"});
        when(joinPoint.proceed()).thenReturn(null);

        Loggable l = loggable("", Level.INFO, true, false, new String[]{}, 1.0);
        // Should not throw; masking is applied to first sensitive param
        assertThat(aspect.logExecution(joinPoint, l)).isNull();
    }

    @Test
    void masksSensitiveParameterShortValueShowsAll() throws Throwable {
        Method m = method(SampleTarget.class, "login", String.class, String.class);
        when(signature.getMethod()).thenReturn(m);
        // "ab" length 2, showFirst+showLast = 4 >= length -> returned as-is
        when(joinPoint.getArgs()).thenReturn(new Object[]{"ab", "u"});
        when(joinPoint.proceed()).thenReturn(null);

        Loggable l = loggable("", Level.INFO, true, false, new String[]{"password"}, 1.0);
        assertThat(aspect.logExecution(joinPoint, l)).isNull();
    }

    @Test
    void masksSensitiveParameterFullMask() throws Throwable {
        Method m = method(SampleTarget.class, "secret", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"abcdef"});
        when(joinPoint.proceed()).thenReturn(null);

        Loggable l = loggable("", Level.INFO, true, false, new String[]{}, 1.0);
        assertThat(aspect.logExecution(joinPoint, l)).isNull();
    }

    @Test
    void masksSensitiveParameterNullValue() throws Throwable {
        Method m = method(SampleTarget.class, "secret", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{null});
        when(joinPoint.proceed()).thenReturn(null);

        Loggable l = loggable("", Level.ERROR, true, false, new String[]{}, 1.0);
        assertThat(aspect.logExecution(joinPoint, l)).isNull();
    }

    @Test
    void jsonSerializationFailureIsHandled() throws Throwable {
        Method m = method(SampleTarget.class, "greet", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"z"});
        // returning a bean Jackson cannot serialize (empty bean) triggers the catch in toJsonString
        Object unserializable = new Object();
        when(joinPoint.proceed()).thenReturn(unserializable);

        Loggable l = loggable("", Level.TRACE, false, true, new String[]{}, 1.0);
        Object result = aspect.logExecution(joinPoint, l);
        assertThat(result).isSameAs(unserializable);
    }

    @Test
    void resolvesMethodLevelAnnotationWhenParamNull() throws Throwable {
        Method m = method(MethodAnnotated.class, "doWork");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn("ok");

        Object result = aspect.logExecution(joinPoint, null);
        assertThat(result).isEqualTo("ok");
    }

    @Test
    void resolvesClassLevelAnnotationWhenParamNull() throws Throwable {
        Method m = method(ClassAnnotated.class, "run");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{});
        when(joinPoint.proceed()).thenReturn(null);

        Object result = aspect.logExecution(joinPoint, null);
        assertThat(result).isNull();
    }

    @Test
    void proceedsWhenNoAnnotationFound() throws Throwable {
        Method m = method(SampleTarget.class, "greet", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.proceed()).thenReturn("plain");

        Object result = aspect.logExecution(joinPoint, null);
        assertThat(result).isEqualTo("plain");
    }
}
