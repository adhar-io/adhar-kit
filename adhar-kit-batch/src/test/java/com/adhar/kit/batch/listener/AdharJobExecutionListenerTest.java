package com.adhar.kit.batch.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;

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
}
