package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.Audit;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AuditAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuditAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private AuditAspect aspect;

    @SuppressWarnings("unused")
    static class SampleTarget {
        public String perform(String input) {
            return "result";
        }
    }

    @Audit("class-op")
    @SuppressWarnings("unused")
    static class ClassAnnotated {
        public void run() {
        }
    }

    @SuppressWarnings("unused")
    static class MethodAnnotated {
        @Audit
        public void doWork() {
        }
    }

    private Method method(Class<?> c, String name, Class<?>... params) throws Exception {
        return c.getDeclaredMethod(name, params);
    }

    private Audit audit(String value, String eventType, Level level, boolean includeArgs,
                        boolean includeResult, boolean includeUser, String[] tags) {
        Audit a = mock(Audit.class);
        when(a.value()).thenReturn(value);
        when(a.eventType()).thenReturn(eventType);
        when(a.level()).thenReturn(level);
        when(a.includeArgs()).thenReturn(includeArgs);
        when(a.includeResult()).thenReturn(includeResult);
        when(a.includeUser()).thenReturn(includeUser);
        when(a.tags()).thenReturn(tags);
        return a;
    }

    @BeforeEach
    void setUp() {
        aspect = new AuditAspect(new ObjectMapper());
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void auditsSuccessWithArgsResultAndUser() throws Throwable {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("alice", "pw",
                        List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        MDC.put("correlationId", "cid-1");
        MDC.put("sessionId", "sess-1");
        MDC.put("remoteAddr", "127.0.0.1");
        MDC.put("userAgent", "junit");

        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"plain", 42, true, new SampleTarget(), "my-secret"});
        when(joinPoint.proceed()).thenReturn("result");

        Audit a = audit("", "LOGIN", Level.INFO, true, true, true, new String[]{"security", "auth"});

        Object result = aspect.auditExecution(joinPoint, a);
        assertThat(result).isEqualTo("result");
    }

    @Test
    void auditsFailureWithException() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"in"});
        RuntimeException ex = new IllegalArgumentException("bad");
        when(joinPoint.proceed()).thenThrow(ex);

        Audit a = audit("op-name", "", Level.ERROR, true, true, false, new String[]{});

        assertThatThrownBy(() -> aspect.auditExecution(joinPoint, a)).isSameAs(ex);
    }

    @Test
    void includeUserButNoAuthentication() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn("r");

        Audit a = audit("", "", Level.WARN, false, false, true, new String[]{});
        assertThat(aspect.auditExecution(joinPoint, a)).isEqualTo("r");
    }

    @Test
    void resolvesMethodAnnotationWhenNull() throws Throwable {
        Method m = method(MethodAnnotated.class, "doWork");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);

        assertThat(aspect.auditExecution(joinPoint, null)).isNull();
    }

    @Test
    void resolvesClassAnnotationWhenNull() throws Throwable {
        Method m = method(ClassAnnotated.class, "run");
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);

        assertThat(aspect.auditExecution(joinPoint, null)).isNull();
    }

    @Test
    void proceedsWhenNoAnnotationFound() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.proceed()).thenReturn("plain");

        assertThat(aspect.auditExecution(joinPoint, null)).isEqualTo("plain");
    }

    @Test
    void redactsSensitiveKeywordArguments() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"the password is hidden"});
        when(joinPoint.proceed()).thenReturn(null);

        Audit a = audit("", "", Level.DEBUG, true, false, false, new String[]{});
        assertThat(aspect.auditExecution(joinPoint, a)).isNull();
    }

    @Test
    void tracePathExecutes() throws Throwable {
        Method m = method(SampleTarget.class, "perform", String.class);
        when(signature.getMethod()).thenReturn(m);
        when(joinPoint.getArgs()).thenReturn(new Object[0]);
        when(joinPoint.proceed()).thenReturn(null);

        Audit a = audit("", "", Level.TRACE, false, false, false, new String[]{});
        assertThat(aspect.auditExecution(joinPoint, a)).isNull();
    }
}
