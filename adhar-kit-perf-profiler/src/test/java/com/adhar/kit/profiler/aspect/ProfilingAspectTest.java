package com.adhar.kit.profiler.aspect;

import com.adhar.kit.profiler.annotation.Profiled;
import com.adhar.kit.profiler.config.PerfProfilerProperties;
import com.adhar.kit.profiler.event.SlowCallEvent;
import com.adhar.kit.profiler.event.SlowCallThresholdBreachedEvent;
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
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.annotation.Annotation;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Test
    @DisplayName("sampleRate=0 skips timing/recording entirely but still runs the underlying method")
    void sampleRateZeroSkipsRecording() throws Throwable {
        PerfProfilerProperties props = new PerfProfilerProperties();
        props.setSampleRate(0.0);
        ProfilingAspect unsampled = new ProfilingAspect(meterRegistry, profilingRegistry, null, props);

        when(joinPoint.proceed()).thenReturn("result");
        Object result = unsampled.profileMethod(joinPoint, profiled("", true, 500, false));

        assertThat(result).isEqualTo("result");
        assertThat(profilingRegistry.getReport().totalProfiledCalls()).isZero();
        assertThat(meterRegistry.find("adhar.profiler.SampleService.doWork").timer()).isNull();
    }

    @Test
    @DisplayName("sampleRate=1.0 (default) always times and records every call")
    void sampleRateOneAlwaysRecords() throws Throwable {
        PerfProfilerProperties props = new PerfProfilerProperties();
        props.setSampleRate(1.0);
        ProfilingAspect sampled = new ProfilingAspect(meterRegistry, profilingRegistry, null, props);

        when(joinPoint.proceed()).thenReturn("result");
        for (int i = 0; i < 5; i++) {
            sampled.profileMethod(joinPoint, profiled("", true, 500, false));
        }

        assertThat(profilingRegistry.getReport().totalProfiledCalls()).isEqualTo(5);
    }

    @Test
    @DisplayName("maxTrackedMethods caps distinct methods recorded into the registry")
    void maxTrackedMethodsCapsDistinctMethods() throws Throwable {
        PerfProfilerProperties props = new PerfProfilerProperties();
        props.setMaxTrackedMethods(2);
        ProfilingAspect capped = new ProfilingAspect(meterRegistry, profilingRegistry, null, props);

        for (String methodName : List.of("m1", "m2", "m3", "m4")) {
            ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
            MethodSignature sig = mock(MethodSignature.class);
            when(jp.getSignature()).thenReturn(sig);
            when(sig.getName()).thenReturn(methodName);
            doReturn(SampleService.class).when(sig).getDeclaringType();
            when(jp.proceed()).thenReturn("ok");

            capped.profileMethod(jp, profiled("", true, 500, false));
        }

        ProfilingReport report = profilingRegistry.getReport(Integer.MAX_VALUE);
        assertThat(report.methodCallCounts()).hasSize(2);
        assertThat(report.totalProfiledCalls()).isEqualTo(2);
    }

    @Test
    @DisplayName("a per-call slow execution publishes a SlowCallEvent in addition to logging")
    void slowCallPublishesSlowCallEvent() throws Throwable {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ProfilingAspect evented = new ProfilingAspect(
                meterRegistry, profilingRegistry, publisher, new PerfProfilerProperties());

        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(15);
            return "slow";
        });
        Profiled annotation = profiled("", true, 1, false);

        evented.profileMethod(joinPoint, annotation);

        ArgumentCaptor<SlowCallEvent> captor = ArgumentCaptor.forClass(SlowCallEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(captor.capture());
        SlowCallEvent event = captor.getValue();
        assertThat(event.getClassName()).isEqualTo("SampleService");
        assertThat(event.getMethodName()).isEqualTo("doWork");
        assertThat(event.getThresholdMs()).isEqualTo(1);
        assertThat(event.getDurationMs()).isGreaterThan(1);
        assertThat(event.getMethodKey()).isEqualTo("SampleService.doWork");
    }

    @Test
    @DisplayName("a fast call under threshold does not publish a SlowCallEvent")
    void fastCallDoesNotPublishSlowCallEvent() throws Throwable {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ProfilingAspect evented = new ProfilingAspect(
                meterRegistry, profilingRegistry, publisher, new PerfProfilerProperties());

        when(joinPoint.proceed()).thenReturn("fast");
        Profiled annotation = profiled("", true, 500_000, false);

        evented.profileMethod(joinPoint, annotation);

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(SlowCallEvent.class));
    }

    @Test
    @DisplayName("a sustained aggregate p99 breach publishes a debounced SlowCallThresholdBreachedEvent")
    void aggregateP99BreachPublishesThresholdEvent() throws Throwable {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PerfProfilerProperties props = new PerfProfilerProperties();
        props.setP99AlertThresholdMs(5);
        ProfilingAspect evented = new ProfilingAspect(meterRegistry, profilingRegistry, publisher, props);

        for (int i = 0; i < 20; i++) {
            ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
            MethodSignature sig = mock(MethodSignature.class);
            when(jp.getSignature()).thenReturn(sig);
            when(sig.getName()).thenReturn("doWork");
            doReturn(SampleService.class).when(sig).getDeclaringType();
            boolean slow = i >= 18;
            when(jp.proceed()).thenAnswer(invocation -> {
                if (slow) {
                    Thread.sleep(20);
                }
                return "v";
            });
            // logSlow=false and a very high per-call threshold to isolate the aggregate-threshold path.
            evented.profileMethod(jp, profiled("", false, 1_000_000, false));
        }

        ArgumentCaptor<SlowCallThresholdBreachedEvent> captor =
                ArgumentCaptor.forClass(SlowCallThresholdBreachedEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(captor.capture());
        SlowCallThresholdBreachedEvent event = captor.getValue();
        assertThat(event.getMethodKey()).isEqualTo("SampleService.doWork");
        assertThat(event.getThresholdMs()).isEqualTo(5);
        assertThat(event.getP99Ms()).isGreaterThan(5);
    }

    @Test
    @DisplayName("p99 alert threshold of zero disables aggregate breach events")
    void zeroP99ThresholdDisablesAggregateEvents() throws Throwable {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        PerfProfilerProperties props = new PerfProfilerProperties();
        props.setP99AlertThresholdMs(0);
        ProfilingAspect evented = new ProfilingAspect(meterRegistry, profilingRegistry, publisher, props);

        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(10);
            return "v";
        });
        evented.profileMethod(joinPoint, profiled("", false, 1_000_000, false));

        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any(SlowCallThresholdBreachedEvent.class));
    }

    /** Dummy declaring type used to drive getSimpleName(). */
    private static final class SampleService {
    }
}
