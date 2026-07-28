package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.event.BatchJobFailedEvent;
import com.adhar.kit.batch.metrics.BatchMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AdharJobExecutionListener}.
 *
 * <p>The listener has no externally observable return values; it logs the job
 * lifecycle. These tests exercise every branch (start, successful completion,
 * failure with/without exit description, failure with exceptions, and missing
 * timing information) and assert that no exception escapes the listener.</p>
 */
class AdharJobExecutionListenerTest {

    private final AdharJobExecutionListener listener = new AdharJobExecutionListener();

    private JobExecution execution(BatchStatus status, ExitStatus exitStatus,
                                   LocalDateTime start, LocalDateTime end) {
        var jobInstance = new JobInstance(1L, "sampleJob");
        JobParameters params = new JobParametersBuilder()
                .addString("inputFile", "/data/in.csv")
                .toJobParameters();
        var execution = new JobExecution(42L, jobInstance, params);
        execution.setStatus(status);
        execution.setExitStatus(exitStatus);
        execution.setStartTime(start);
        execution.setEndTime(end);
        return execution;
    }

    @Test
    @DisplayName("beforeJob logs without throwing")
    void beforeJobLogs() {
        var exec = execution(BatchStatus.STARTING, ExitStatus.EXECUTING,
                LocalDateTime.now(), null);

        assertThatCode(() -> listener.beforeJob(exec)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("afterJob logs successful completion with duration")
    void afterJobSuccess() {
        var start = LocalDateTime.now();
        var exec = execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED,
                start, start.plusSeconds(5));

        assertThatCode(() -> listener.afterJob(exec)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("afterJob logs failure with exit description and exceptions")
    void afterJobFailureWithDescriptionAndExceptions() {
        var start = LocalDateTime.now();
        var exitStatus = new ExitStatus("FAILED", "Something broke");
        var exec = execution(BatchStatus.FAILED, exitStatus,
                start, start.plusSeconds(2));
        exec.addFailureException(new IllegalStateException("boom"));

        assertThatCode(() -> listener.afterJob(exec)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("afterJob handles failure with blank description and no exceptions")
    void afterJobFailureBlankDescriptionNoExceptions() {
        var start = LocalDateTime.now();
        var exec = execution(BatchStatus.FAILED, new ExitStatus("FAILED", ""),
                start, start.plusSeconds(1));

        assertThatCode(() -> listener.afterJob(exec)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("afterJob uses Duration.ZERO when start or end time is missing")
    void afterJobMissingTimes() {
        var exec = execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, null, null);

        assertThatCode(() -> listener.afterJob(exec)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("afterJob records a successful execution to metrics")
    void afterJobRecordsSuccessMetrics() {
        var metrics = mock(BatchMetrics.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var listener = new AdharJobExecutionListener(metrics, publisher);
        var start = LocalDateTime.now();
        var exec = execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, start, start.plusSeconds(3));

        listener.afterJob(exec);

        verify(metrics).recordJobExecution(eq("sampleJob"), org.mockito.ArgumentMatchers.anyLong(), eq(true));
        verify(publisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("afterJob records failure to metrics and publishes a BatchJobFailedEvent")
    void afterJobRecordsFailureAndPublishesEvent() {
        var metrics = mock(BatchMetrics.class);
        var publisher = mock(ApplicationEventPublisher.class);
        var listener = new AdharJobExecutionListener(metrics, publisher);
        var start = LocalDateTime.now();
        var exec = execution(BatchStatus.FAILED, new ExitStatus("FAILED", "broke"),
                start, start.plusSeconds(1));
        exec.addFailureException(new IllegalStateException("boom"));

        listener.afterJob(exec);

        verify(metrics).recordJobExecution(eq("sampleJob"), org.mockito.ArgumentMatchers.anyLong(), eq(false));

        ArgumentCaptor<BatchJobFailedEvent> captor = ArgumentCaptor.forClass(BatchJobFailedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.getJobName()).isEqualTo("sampleJob");
        assertThat(event.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(event.getExitCode()).isEqualTo("FAILED");
        assertThat(event.getFailureExceptions()).hasSize(1);
    }

    @Test
    @DisplayName("afterJob tolerates a failing metrics collector")
    void afterJobToleratesMetricsFailure() {
        var metrics = mock(BatchMetrics.class);
        org.mockito.Mockito.doThrow(new RuntimeException("registry down"))
                .when(metrics).recordJobExecution(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyBoolean());
        var listener = new AdharJobExecutionListener(metrics, null);
        var start = LocalDateTime.now();
        var exec = execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, start, start.plusSeconds(1));

        assertThatCode(() -> listener.afterJob(exec)).doesNotThrowAnyException();
    }
}
