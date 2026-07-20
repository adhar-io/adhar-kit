package com.adhar.adharkit.logging.aspect;

import com.adhar.adharkit.logging.annotation.LogBatchJob;
import com.adhar.adharkit.logging.batch.BatchJobLogger;
import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.RecordingAppLogEventSink;
import com.adhar.adharkit.logging.masking.LogDataMasker;
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
 * Unit tests for {@link LogBatchJobAspect}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogBatchJobAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private MethodSignature signature;

    private RecordingAppLogEventSink sink;
    private LogBatchJobAspect aspect;

    @SuppressWarnings("unused")
    static class SampleJobs {
        @LogBatchJob("nightly-job")
        public void nightly() {
        }

        @LogBatchJob
        public void defaultNamed() {
        }

        public void notAnnotated() {
        }
    }

    @BeforeEach
    void setUp() {
        AdharLoggingProperties properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        aspect = new LogBatchJobAspect(new BatchJobLogger(properties,
                new AppLogEventPublisher(properties, new LogDataMasker(properties.getMasking()),
                        List.of(sink))));
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    private void givenMethod(String name) throws Exception {
        Method method = SampleJobs.class.getDeclaredMethod(name);
        when(signature.getMethod()).thenReturn(method);
    }

    @Test
    void successfulJobPublishesStartedAndCompleted() throws Throwable {
        givenMethod("nightly");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.logBatchJob(joinPoint);

        List<AppLogEvent> events = sink.getEvents();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getOutcome()).isEqualTo(AppLogEventOutcome.STARTED);
        assertThat(events.get(0).getName()).isEqualTo("nightly-job");
        assertThat(events.get(1).getMessage()).isEqualTo("COMPLETED");
        assertThat(events.get(1).getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
    }

    @Test
    void failedJobPublishesFailureAndRethrows() throws Throwable {
        givenMethod("nightly");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("source unavailable"));

        assertThatThrownBy(() -> aspect.logBatchJob(joinPoint))
                .isInstanceOf(IllegalStateException.class);

        AppLogEvent summary = sink.last();
        assertThat(summary.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(summary.getErrorMessage()).isEqualTo("source unavailable");
    }

    @Test
    void defaultsJobNameToClassAndMethod() throws Throwable {
        givenMethod("defaultNamed");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.logBatchJob(joinPoint);

        assertThat(sink.getEvents().get(0).getName()).isEqualTo("SampleJobs.defaultNamed");
    }

    @Test
    void notAnnotatedMethodProceedsWithoutEvents() throws Throwable {
        givenMethod("notAnnotated");
        when(joinPoint.proceed()).thenReturn(null);

        aspect.logBatchJob(joinPoint);

        assertThat(sink.getEvents()).isEmpty();
    }
}
