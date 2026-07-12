package com.adhar.kit.ai.aspect;

import com.adhar.kit.ai.annotation.AiMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AiMetricsAspect} using an in-memory meter registry.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiMetricsAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    private SimpleMeterRegistry registry;
    private AiMetricsAspect aspect;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        aspect = new AiMetricsAspect(registry);
    }

    private AiMetrics annotation(String name, boolean latency, boolean success,
                                 long latencyThreshold, String alertEmail) {
        AiMetrics ann = mock(AiMetrics.class);
        when(ann.metricName()).thenReturn(name);
        when(ann.trackLatency()).thenReturn(latency);
        when(ann.trackSuccessRate()).thenReturn(success);
        when(ann.latencyAlertThreshold()).thenReturn(latencyThreshold);
        when(ann.alertEmail()).thenReturn(alertEmail);
        when(ann.tags()).thenReturn(new String[]{});
        return ann;
    }

    @Test
    void recordsLatencyAndSuccessOnHappyPath() throws Throwable {
        when(joinPoint.proceed()).thenReturn("result");

        Object result = aspect.processMetricsAnnotation(
                joinPoint, annotation("ai.test", true, true, 0, ""));

        assertThat(result).isEqualTo("result");
        assertThat(registry.find("ai.test.latency").timer()).isNotNull();
        assertThat(registry.find("ai.test.total").tag("success", "true").counter()).isNotNull();
    }

    @Test
    void usesDefaultMetricNameWhenEmpty() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.processMetricsAnnotation(joinPoint, annotation("", true, true, 0, ""));

        assertThat(registry.find("ai.operation.latency").timer()).isNotNull();
    }

    @Test
    void recordsFailureWhenMethodThrows() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("fail"));

        assertThatThrownBy(() -> aspect.processMetricsAnnotation(
                joinPoint, annotation("ai.err", true, true, 0, "")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(registry.find("ai.err.total").tag("success", "false").counter()).isNotNull();
    }

    @Test
    void firesAlertWhenLatencyThresholdExceeded() throws Throwable {
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(20);
            return "slow";
        });

        Object result = aspect.processMetricsAnnotation(
                joinPoint, annotation("ai.slow", true, false, 1, "admin@example.com"));

        assertThat(result).isEqualTo("slow");
        assertThat(registry.find("ai.slow.latency").timer()).isNotNull();
    }

    @Test
    void skipsTrackingWhenDisabled() throws Throwable {
        when(joinPoint.proceed()).thenReturn("ok");

        aspect.processMetricsAnnotation(joinPoint, annotation("ai.none", false, false, 0, ""));

        assertThat(registry.find("ai.none.latency").timer()).isNull();
        assertThat(registry.find("ai.none.total").counter()).isNull();
    }
}
