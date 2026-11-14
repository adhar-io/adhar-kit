package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.annotation.ParamSource;
import com.adhar.kit.batch.annotation.ParamType;
import com.adhar.kit.batch.event.BatchJobFailedEvent;
import com.adhar.kit.batch.params.JobParameterSpec;
import com.adhar.kit.batch.params.JobParameterSpec.Param;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.support.DebugLogCapture;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link AdharJobExecutionListener}.
 */
class AdharJobExecutionListenerTest {

    private final BatchRunObserver observer = mock(BatchRunObserver.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final AdharJobExecutionListener listener = new AdharJobExecutionListener(publisher, List.of(observer), null);

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
    @DisplayName("beforeJob notifies the observers")
    void beforeJobNotifies() {
        var exec = execution(BatchStatus.STARTING, ExitStatus.EXECUTING, LocalDateTime.now(), null);

        listener.beforeJob(exec);

        verify(observer).jobStarted(exec);
    }

    @Test
    @DisplayName("beforeJob's DEBUG line masks a parameter the job declares sensitive, not only secret-like names")
    void beforeJobMasksSensitiveParameters() {
        var registry = JobParameterSpecRegistry.of(List.of(new JobParameterSpec("sampleJob", "", List.of(
                new Param("account", ParamType.STRING, true, false, null, null, List.of(), ParamSource.INPUT, true, "")),
                false)));
        var masking = new AdharJobExecutionListener(null, List.of(observer), registry);
        var exec = new JobExecution(42L, new JobInstance(1L, "sampleJob"), new JobParametersBuilder()
                .addString("account", "acct-S3CRET").addString("region", "sg").toJobParameters());

        try (var log = new DebugLogCapture(AdharJobExecutionListener.class)) {
            masking.beforeJob(exec);

            assertThat(log.messages()).anySatisfy(line -> assertThat(line).contains("account=****", "region=sg"))
                    .noneSatisfy(line -> assertThat(line).contains("S3CRET"));
        }
    }

    @Test
    @DisplayName("afterJob on success notifies the observers and publishes nothing")
    void afterJobSuccess() {
        var start = LocalDateTime.now();
        var exec = execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, start, start.plusSeconds(5));

        listener.afterJob(exec);

        verify(observer).jobFinished(exec);
        verify(publisher, never()).publishEvent(any(Object.class));
    }

    @Test
    @DisplayName("afterJob on failure publishes a BatchJobFailedEvent")
    void afterJobFailurePublishesEvent() {
        var start = LocalDateTime.now();
        var exec = execution(BatchStatus.FAILED, new ExitStatus("FAILED", "broke"), start, start.plusSeconds(1));
        exec.addFailureException(new IllegalStateException("boom"));

        listener.afterJob(exec);

        verify(observer).jobFinished(exec);
        ArgumentCaptor<BatchJobFailedEvent> captor = ArgumentCaptor.forClass(BatchJobFailedEvent.class);
        verify(publisher).publishEvent(captor.capture());
        var event = captor.getValue();
        assertThat(event.getJobName()).isEqualTo("sampleJob");
        assertThat(event.getJobExecutionId()).isEqualTo(42L);
        assertThat(event.getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(event.getExitCode()).isEqualTo("FAILED");
        assertThat(event.getExitDescription()).isEqualTo("broke");
        assertThat(event.getDurationMs()).isEqualTo(1000L);
        assertThat(event.getFailureExceptions()).hasSize(1);
    }

    @Test
    @DisplayName("afterJob on failure tolerates having no publisher")
    void afterJobFailureWithoutPublisher() {
        var noPublisher = new AdharJobExecutionListener(null, List.of(observer), null);
        var exec = execution(BatchStatus.FAILED, ExitStatus.FAILED, null, null);

        assertThatCode(() -> noPublisher.afterJob(exec)).doesNotThrowAnyException();
        verify(observer).jobFinished(exec);
    }

    @Test
    @DisplayName("a publisher that fails does not fail the run")
    void afterJobToleratesPublisherFailure() {
        doThrow(new RuntimeException("listener down")).when(publisher).publishEvent(any(Object.class));
        var exec = execution(BatchStatus.FAILED, ExitStatus.FAILED, null, null);

        assertThatCode(() -> listener.afterJob(exec)).doesNotThrowAnyException();
        verify(observer).jobFinished(exec);
    }

    @Test
    @DisplayName("an observer that fails does not fail the run nor stop the next observer")
    void observerFailureIsIsolated() {
        var next = mock(BatchRunObserver.class);
        var exec = execution(BatchStatus.COMPLETED, ExitStatus.COMPLETED, null, null);
        doThrow(new RuntimeException("registry down")).when(observer).jobFinished(exec);
        var isolating = new AdharJobExecutionListener(null, List.of(observer, next), null);

        assertThatCode(() -> isolating.afterJob(exec)).doesNotThrowAnyException();
        verify(next).jobFinished(exec);
    }
}
