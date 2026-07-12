package com.adhar.kit.profiler.aspect;

import com.adhar.kit.profiler.annotation.Profiled;
import com.adhar.kit.profiler.model.ProfilingReport;
import com.adhar.kit.profiler.registry.ProfilingRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.annotation.Annotation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProfilingAspectTest {

    private SimpleMeterRegistry meterRegistry;
    private ProfilingRegistry profilingRegistry;
    private ProfilingAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        profilingRegistry = new ProfilingRegistry();
        aspect = new ProfilingAspect(meterRegistry, profilingRegistry);

        joinPoint = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("doWork");
        doReturn(SampleService.class).when(signature).getDeclaringType();
    }

    /** Build a Profiled annotation with the given attribute values. */
    private Profiled profiled(String value, boolean logSlow, long slowThresholdMs, boolean histogram) {
        return new Profiled() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return Profiled.class;
            }

            @Override
            public String value() {
                return value;
            }

            @Override
            public boolean logSlow() {
                return logSlow;
            }

            @Override
            public long slowThresholdMs() {
                return slowThresholdMs;
            }

            @Override
            public boolean histogram() {
                return histogram;
            }
        };
    }

    @Test
    @DisplayName("profileMethod records a successful execution in registry and meter")
    void profileMethodRecordsSuccess() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");
        Profiled annotation = profiled("", true, 500, false);

        Object result = aspect.profileMethod(joinPoint, annotation);

        assertThat(result).isEqualTo("result");

        ProfilingReport report = profilingRegistry.getReport();
        assertThat(report.totalProfiledCalls()).isEqualTo(1);
        assertThat(report.methodCallCounts()).containsKey("SampleService.doWork");

        Timer timer = meterRegistry.find("adhar.profiler.SampleService.doWork").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.getId().getTag("class")).isEqualTo("SampleService");
        assertThat(timer.getId().getTag("method")).isEqualTo("doWork");
        assertThat(timer.getId().getTag("success")).isEqualTo("true");
    }

    @Test
    @DisplayName("custom metric name from annotation value is used for the timer")
    void profileMethodUsesCustomMetricName() throws Throwable {
        when(joinPoint.proceed()).thenReturn(42);
        Profiled annotation = profiled("custom.metric", true, 500, false);

        aspect.profileMethod(joinPoint, annotation);

        assertThat(meterRegistry.find("adhar.profiler.custom.metric").timer()).isNotNull();
        assertThat(meterRegistry.find("adhar.profiler.SampleService.doWork").timer()).isNull();
    }

    @Test
    @DisplayName("histogram=true registers a timer carrying percentile histogram config")
    void profileMethodWithHistogram() throws Throwable {
        when(joinPoint.proceed()).thenReturn("x");
        Profiled annotation = profiled("", true, 500, true);

        aspect.profileMethod(joinPoint, annotation);

        Timer timer = meterRegistry.find("adhar.profiler.SampleService.doWork").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("exception is propagated and recorded as a failed call with error tag")
    void profileMethodRecordsFailure() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));
        Profiled annotation = profiled("", true, 500, false);

        assertThatThrownBy(() -> aspect.profileMethod(joinPoint, annotation))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        ProfilingReport report = profilingRegistry.getReport();
        assertThat(report.totalProfiledCalls()).isEqualTo(1);
        assertThat(report.errorRates().get("SampleService.doWork")).isEqualTo(1.0);

        Timer timer = meterRegistry.find("adhar.profiler.SampleService.doWork").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTag("success")).isEqualTo("false");
        assertThat(timer.getId().getTag("error")).isEqualTo("IllegalStateException");
    }

    @Test
    @DisplayName("logSlow with a zero threshold still records and proceeds normally")
    void profileMethodLogsSlowExecution() throws Throwable {
        when(joinPoint.proceed()).thenReturn("slow");
        // threshold 0 means any non-zero duration counts as slow, exercising the warn branch
        Profiled annotation = profiled("", true, 0, false);

        Object result = aspect.profileMethod(joinPoint, annotation);

        assertThat(result).isEqualTo("slow");
        assertThat(profilingRegistry.getReport().totalProfiledCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("logSlow=false skips the slow-warning branch but still records")
    void profileMethodLogSlowDisabled() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");
        Profiled annotation = profiled("", false, 0, false);

        aspect.profileMethod(joinPoint, annotation);

        assertThat(profilingRegistry.getReport().totalProfiledCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("profileClass delegates to the same profiling logic")
    void profileClassRecordsExecution() throws Throwable {
        when(joinPoint.proceed()).thenReturn("classLevel");
        Profiled annotation = profiled("", true, 500, false);

        Object result = aspect.profileClass(joinPoint, annotation);

        assertThat(result).isEqualTo("classLevel");
        assertThat(profilingRegistry.getReport().totalProfiledCalls()).isEqualTo(1);
        assertThat(meterRegistry.find("adhar.profiler.SampleService.doWork").timer()).isNotNull();
    }

    /** Dummy declaring type used to drive getSimpleName(). */
    private static final class SampleService {
    }
}
