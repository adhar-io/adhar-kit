package com.adhar.kit.metrics.auto;

import com.adhar.kit.metrics.annotation.MonitorPerformance;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Behavioural unit tests for {@link MetricsInterceptor} using mocked join points.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricsInterceptorTest {

    private SimpleMeterRegistry registry;
    private MetricsInterceptor interceptor;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        interceptor = new MetricsInterceptor(registry);
    }

    // ---- Fixtures -----------------------------------------------------------

    @Measured(value = "order.create", module = "persistence", tags = {"tier", "data"})
    public Object measuredWithValue() {
        return "ok";
    }

    @Measured
    public Object measuredDefault() {
        return "ok";
    }

    @MonitorPerformance(name = "perf.op", tags = {"k", "v"})
    public Object profiledWithName() {
        return "ok";
    }

    @MonitorPerformance
    public Object profiledDefault() {
        return "ok";
    }

    private ProceedingJoinPoint jpFor(String methodName, Object returnValue) throws Throwable {
        Method method = MetricsInterceptorTest.class.getMethod(methodName);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(sig);
        when(jp.getTarget()).thenReturn(this);
        when(jp.proceed()).thenReturn(returnValue);
        return jp;
    }

    private ProceedingJoinPoint jpThrowing(String methodName, Throwable error) throws Throwable {
        Method method = MetricsInterceptorTest.class.getMethod(methodName);
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(method);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(sig);
        when(jp.getTarget()).thenReturn(this);
        when(jp.proceed()).thenThrow(error);
        return jp;
    }

    // ---- @Measured ----------------------------------------------------------

    @Test
    void aroundMeasuredRecordsDurationAndCountWithExplicitOperation() throws Throwable {
        Object result = interceptor.aroundMeasured(jpFor("measuredWithValue", "ok"));

        assertThat(result).isEqualTo("ok");
        assertThat(registry.find("adhar.operation.duration")
                .tag("module", "persistence")
                .tag("operation", "order.create")
                .tag("tier", "data")
                .timer().count()).isEqualTo(1L);
        assertThat(registry.find("adhar.operation.count")
                .tag("operation", "order.create").counter().count()).isEqualTo(1.0);
    }

    @Test
    void aroundMeasuredUsesDefaultsWhenAttributesEmpty() throws Throwable {
        interceptor.aroundMeasured(jpFor("measuredDefault", "ok"));

        assertThat(registry.find("adhar.operation.count")
                .tag("module", "general")
                .tag("operation", "MetricsInterceptorTest.measuredDefault")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void aroundMeasuredRecordsErrorOnException() throws Throwable {
        assertThatThrownBy(() -> interceptor.aroundMeasured(
                jpThrowing("measuredWithValue", new IllegalStateException("boom"))))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.find("adhar.operation.errors")
                .tag("exception", "IllegalStateException").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("adhar.operation.count")
                .tag("success", "false").counter().count()).isEqualTo(1.0);
    }

    // ---- @MonitorPerformance ------------------------------------------------

    @Test
    void aroundProfiledUsesAnnotationName() throws Throwable {
        Object result = interceptor.aroundProfiled(jpFor("profiledWithName", "ok"));

        assertThat(result).isEqualTo("ok");
        assertThat(registry.find("adhar.operation.duration")
                .tag("module", "profiled")
                .tag("operation", "perf.op")
                .tag("k", "v")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void aroundProfiledFallsBackToClassAndMethodName() throws Throwable {
        interceptor.aroundProfiled(jpFor("profiledDefault", "ok"));

        assertThat(registry.find("adhar.operation.count")
                .tag("operation", "MetricsInterceptorTest.profiledDefault")
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void aroundProfiledRecordsErrorOnException() throws Throwable {
        assertThatThrownBy(() -> interceptor.aroundProfiled(
                jpThrowing("profiledWithName", new RuntimeException("x"))))
                .isInstanceOf(RuntimeException.class);

        assertThat(registry.find("adhar.operation.errors").counter().count()).isEqualTo(1.0);
    }
}
