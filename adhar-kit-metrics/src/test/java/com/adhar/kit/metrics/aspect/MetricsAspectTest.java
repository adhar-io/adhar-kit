package com.adhar.kit.metrics.aspect;

import com.adhar.kit.metrics.annotation.Timed;
import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import com.adhar.kit.metrics.util.MetricsUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link MetricsAspect}, the simple {@link Timed} timing aspect.
 * <p>
 * A real {@link SimpleMeterRegistry} and {@link MetricsUtils} are used, while the
 * {@link ProceedingJoinPoint} is mocked and backed by real annotated methods on
 * {@link Service} so the annotation and signature resolution behave like runtime.
 * </p>
 */
class MetricsAspectTest {

    private SimpleMeterRegistry registry;
    private MetricsAspect aspect;
    private final Service target = new Service();

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        MetricsUtils utils = new MetricsUtils(registry, new AdharMetricsProperties());
        aspect = new MetricsAspect(registry, utils);
    }

    private ProceedingJoinPoint jp(String methodName, Object proceedResult, Throwable toThrow) {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        MethodSignature sig = mock(MethodSignature.class);
        Method m;
        try {
            m = Service.class.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        lenient().when(sig.getMethod()).thenReturn(m);
        lenient().when(pjp.getSignature()).thenReturn(sig);
        lenient().when(pjp.getTarget()).thenReturn(target);
        try {
            if (toThrow != null) {
                lenient().when(pjp.proceed()).thenThrow(toThrow);
            } else {
                lenient().when(pjp.proceed()).thenReturn(proceedResult);
            }
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
        return pjp;
    }

    @Test
    void timeMethod_namedWithExplicitTags_recordsTimer() throws Throwable {
        Object result = aspect.timeMethod(jp("named", "ok", null));

        assertThat(result).isEqualTo("ok");
        assertThat(registry.find("aspect.named").tag("a", "b").timer().count()).isEqualTo(1L);
    }

    @Test
    void timeMethod_plain_derivesNameAndDefaultTags() throws Throwable {
        aspect.timeMethod(jp("plain", "ok", null));

        assertThat(registry.find("Service.plain")
                .tag("class", "Service")
                .tag("method", "plain")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void timeMethod_exception_recordsTimerWithExceptionTagAndRethrows() {
        ProceedingJoinPoint pjp = jp("plain", null, new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect.timeMethod(pjp))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(registry.find("Service.plain")
                .tag("exception", "IllegalStateException")
                .timer().count()).isEqualTo(1L);
    }

    @SuppressWarnings("unused")
    static class Service {

        @Timed(name = "aspect.named", tags = {"a", "b"})
        public String named() {
            return "ok";
        }

        @Timed
        public String plain() {
            return "ok";
        }
    }
}
