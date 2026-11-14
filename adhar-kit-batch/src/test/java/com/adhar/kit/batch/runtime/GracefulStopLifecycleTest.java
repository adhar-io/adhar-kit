package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.support.RunnerJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GracefulStopLifecycle}: what the context's close (SIGTERM) does to a running run, in
 * process. {@code RunnerExitCodeIT} proves the same with a real signal in a separate JVM.
 */
class GracefulStopLifecycleTest extends RunnerFixture {

    @Test
    @DisplayName("SIGTERM mid-run: the execution stops at a chunk boundary as STOPPED, the summary is written first, "
            + "and the run can be resumed to COMPLETED")
    void stopThenResume() throws Exception {
        RunnerJobs.CHUNK_MILLIS.set(200);
        env.putAll(java.util.Map.of("ADHAR_BATCH_JOB", RunnerJobs.SLOW_CHUNK_JOB, "ADHAR_BATCH_TRIGGER", "manual",
                "ADHAR_BATCH_PARAMS", "{\"run\":\"sigterm\"}"));
        AdharBatchRunner runner = runner();
        CompletableFuture<Void> run = CompletableFuture.runAsync(() -> runner.run(null));
        await().atMost(Duration.ofSeconds(10)).until(() -> !jdbc.queryForList(
                "SELECT JOB_EXECUTION_ID FROM ADHAR_BATCH_EXECUTION_LEASE", Long.class).isEmpty());

        long stopRequested = System.nanoTime();
        stop.stop();
        long stopReturned = System.nanoTime();

        assertThat(run).succeedsWithin(5, TimeUnit.SECONDS);
        assertThat(stop.isStopping()).isTrue();
        assertThat(Duration.ofNanos(stopReturned - stopRequested)).as("one chunk, not the whole job")
                .isLessThan(Duration.ofSeconds(3));
        RunOutcome stopped = exitCodes.outcome().orElseThrow();
        assertThat(stopped.kind()).isEqualTo(RunOutcome.Kind.STOPPED);
        assertThat(stopped.exitCode()).isEqualTo(RunOutcome.SIGTERM_STATUS);
        JobExecution execution = jobRepository.getJobExecution(stopped.execution().getId());
        assertThat(execution.getStatus()).isEqualTo(BatchStatus.STOPPED);
        assertThat(execution.getStepExecutions().iterator().next().getWriteCount()).isBetween(1L, 49L);
        assertThat(summary()).as("written before stop() returned").contains("\"outcome\":\"stopped\"");

        stop = new GracefulStopLifecycle(jobOperatorProvider, properties.getShutdown().getTimeout());
        stop.start();
        RunOutcome resumed = run();

        assertThat(resumed.exitCode()).isZero();
        assertThat(resumed.execution().getJobInstance().getInstanceId())
                .isEqualTo(execution.getJobInstance().getInstanceId());
        assertThat(resumed.execution().getStepExecutions().iterator().next().getWriteCount())
                .as("resumed from the last commit").isEqualTo(50 - execution.getStepExecutions().iterator().next()
                        .getWriteCount());
    }

    @Test
    @DisplayName("a stop with no run in progress returns at once; a second stop does nothing")
    void idleStop() {
        long started = System.nanoTime();
        stop.stop();
        stop.stop();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        assertThat(stop.isRunning()).isFalse();
        assertThat(stop.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    @DisplayName("an execution that starts after the stop was requested is stopped at once")
    @SuppressWarnings("unchecked")
    void stopBeforeTheExecution() throws Exception {
        JobOperator operator = mock(JobOperator.class);
        ObjectProvider<JobOperator> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(operator);
        GracefulStopLifecycle early = new GracefulStopLifecycle(provider, Duration.ofSeconds(1));
        early.start();
        early.stop();
        JobExecution execution = new JobExecution(5L, new JobInstance(1L, "j"), new JobParameters());

        early.executionStarted(execution);

        verify(operator).stop(execution);
        doThrow(new JobExecutionNotRunningException("ended")).when(operator).stop(execution);
        early.executionStarted(execution);
        early.executionEnded(execution);
    }

    @Test
    @DisplayName("shutdown waits at most adhar.batch.shutdown.timeout for a run that does not finish")
    void boundedWait() {
        GracefulStopLifecycle bounded = new GracefulStopLifecycle(jobOperatorProvider, Duration.ofMillis(200));
        bounded.start();
        bounded.runStarted();

        long started = System.nanoTime();
        bounded.stop();

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isBetween(Duration.ofMillis(150),
                Duration.ofSeconds(2));
    }
}
