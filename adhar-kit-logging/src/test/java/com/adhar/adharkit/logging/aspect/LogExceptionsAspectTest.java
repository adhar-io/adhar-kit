package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogExceptions;
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
 * Unit tests for {@link LogExceptionsAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogExceptionsAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private LogExceptionsAspect aspect;

    @SuppressWarnings("unused")
    static class SampleTarget {
        public String perform(String input) {
            return "ok";
        }
    }

    @LogExceptions("class-op")
    @SuppressWarnings("unused")
    static class ClassAnnotated {
        public void run() {
        }
    }

    @SuppressWarnings("unused")
    static class MethodAnnotated {
        @LogExceptions
        public void doWork() {
        }
    }

    @SuppressWarnings("unchecked")
    private LogExceptions logExceptions(String value, Level level, boolean includeArgs,
                                        boolean includeStackTrace,
                                        Class<? extends Throwable>[] only,
                                        Class<? extends Throwable>[] exclude) {
        LogExceptions a = mock(LogExceptions.class);
        when(a.value()).thenReturn(value);
        when(a.level()).thenReturn(level);
        when(a.includeArgs()).thenReturn(includeArgs);
        when(a.includeStackTrace()).thenReturn(includeStackTrace);
        when(a.only()).thenReturn(only != null ? only : new Class[0]);
        when(a.exclude()).thenReturn(exclude != null ? exclude : new Class[0]);
        return a;
    }

    private Method method(Class<?> c, String name, Class<?>... params) throws Exception {
        return c.getDeclaredMethod(name, params);
    }

    @BeforeEach
    void setUp() {
        aspect = new LogExceptionsAspect(new ObjectMapper());
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void returnsResultWhenNoException() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.proceed()).thenReturn("ok");

        LogExceptions ann = logExceptions("", Level.ERROR, true, true, null, null);
        assertThat(aspect.handleExceptions(joinPoint, ann)).isEqualTo("ok");
    }

    @Test
    void logsExceptionWithArgsAndStackTraceAndRootCause() throws Throwable {
        MDC.put("correlationId", "cid");
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"arg", new char[]{'x'}});
        Throwable root = new IllegalStateException("root");
        Throwable wrapper = new RuntimeException("wrapper", root);
        when(joinPoint.proceed()).thenThrow(wrapper);

        LogExceptions ann = logExceptions("op", Level.ERROR, true, true, null, null);
        assertThatThrownBy(() -> aspect.handleExceptions(joinPoint, ann)).isSameAs(wrapper);
    }

    @Test
    void excludedExceptionIsNotLogged() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        IllegalArgumentException ex = new IllegalArgumentException("ignore");
        when(joinPoint.proceed()).thenThrow(ex);

        @SuppressWarnings("unchecked")
        Class<? extends Throwable>[] exclude = new Class[]{IllegalArgumentException.class};
        LogExceptions ann = logExceptions("", Level.WARN, false, false, null, exclude);
        assertThatThrownBy(() -> aspect.handleExceptions(joinPoint, ann)).isSameAs(ex);
    }

    @Test
    void onlyListMatchingExceptionIsLogged() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        IllegalStateException ex = new IllegalStateException("match");
        when(joinPoint.proceed()).thenThrow(ex);

        @SuppressWarnings("unchecked")
        Class<? extends Throwable>[] only = new Class[]{IllegalStateException.class};
        LogExceptions ann = logExceptions("", Level.INFO, false, false, only, null);
        assertThatThrownBy(() -> aspect.handleExceptions(joinPoint, ann)).isSameAs(ex);
    }

    @Test
    void onlyListNonMatchingExceptionIsNotLogged() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        IllegalStateException ex = new IllegalStateException("no-match");
        when(joinPoint.proceed()).thenThrow(ex);

        @SuppressWarnings("unchecked")
        Class<? extends Throwable>[] only = new Class[]{NullPointerException.class};
        LogExceptions ann = logExceptions("", Level.DEBUG, false, false, only, null);
        assertThatThrownBy(() -> aspect.handleExceptions(joinPoint, ann)).isSameAs(ex);
    }

    @Test
    void sanitizesSensitiveArgumentTypes() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{new PasswordHolder(), "normal"});
        RuntimeException ex = new RuntimeException("boom");
        when(joinPoint.proceed()).thenThrow(ex);

        LogExceptions ann = logExceptions("", Level.TRACE, true, false, null, null);
        assertThatThrownBy(() -> aspect.handleExceptions(joinPoint, ann)).isSameAs(ex);
    }

    @Test
    void resolvesMethodAnnotationWhenNull() throws Throwable {
        Method m = method(MethodAnnotated.class, "doWork");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.proceed()).thenReturn(null);
        assertThat(aspect.handleExceptions(joinPoint, null)).isNull();
    }

    @Test
    void resolvesClassAnnotationWhenNull() throws Throwable {
        Method m = method(ClassAnnotated.class, "run");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.proceed()).thenReturn(null);
        assertThat(aspect.handleExceptions(joinPoint, null)).isNull();
    }

    @Test
    void proceedsWhenNoAnnotationFound() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.proceed()).thenReturn("plain");
        assertThat(aspect.handleExceptions(joinPoint, null)).isEqualTo("plain");
    }

    static class PasswordHolder {
        @Override
        public String toString() {
            return "secret";
        }
    }
}
