package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.TrackPerformance;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.RecordingAppLogEventSink;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.performance.OperationStats;
import com.adhar.adharkit.logging.performance.PerformanceLogger;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TrackPerformanceAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TrackPerformanceAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private RecordingAppLogEventSink sink;
    private PerformanceLogger performanceLogger;
    private TrackPerformanceAspect aspect;

    @SuppressWarnings("unused")
    static class SampleTarget {
        @TrackPerformance("db.query")
        public String query() {
            return "rows";
        }

        @TrackPerformance(slowThresholdMs = 0)
        public void alwaysSlow() {
        }

        public void notAnnotated() {
        }
    }

    @BeforeEach
    void setUp() {
        AdharLoggingProperties properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        performanceLogger = new PerformanceLogger(properties,
                new AppLogEventPublisher(properties, new LogDataMasker(properties.getMasking()),
                        List.of(sink)));
        aspect = new TrackPerformanceAspect(performanceLogger);
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    private void givenMethod(String name, Class<?>... params) throws Exception {
        Method method = SampleTarget.class.getDeclaredMethod(name, params);
        when(signature.getMethod()).thenReturn(method);
    }

    @Test
    void recordsSuccessfulExecution() throws Throwable {
        givenMethod("query");
        when(joinPoint.proceed()).thenReturn("rows");

        Object result = aspect.trackPerformance(joinPoint);

        assertThat(result).isEqualTo("rows");
        OperationStats.Snapshot snap = performanceLogger.snapshot().get("db.query");
        assertThat(snap.count()).isEqualTo(1);
        assertThat(snap.failures()).isZero();
    }

    @Test
    void recordsFailureAndRethrows() throws Throwable {
        givenMethod("query");
        when(joinPoint.proceed()).thenThrow(new RuntimeException("db down"));

        assertThatThrownBy(() -> aspect.trackPerformance(joinPoint))
                .isInstanceOf(RuntimeException.class);

        assertThat(performanceLogger.snapshot().get("db.query").failures()).isEqualTo(1);
    }

    @Test
    void annotationThresholdOverrideTriggersSlowEvent() throws Throwable {
        givenMethod("alwaysSlow");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.trackPerformance(joinPoint);

        assertThat(sink.getEvents()).hasSize(1);
        assertThat(sink.last().getMetadata()).containsEntry("slow", true).containsEntry("thresholdMs", 0L);
        assertThat(sink.last().getName()).isEqualTo("SampleTarget.alwaysSlow");
    }

    @Test
    void notAnnotatedMethodProceedsWithoutRecording() throws Throwable {
        givenMethod("notAnnotated");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.trackPerformance(joinPoint);

        assertThat(performanceLogger.snapshot()).isEmpty();
    }
}
