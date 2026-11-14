package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.BatchRole;
import com.adhar.kit.batch.runtime.RunContext;
import com.adhar.kit.batch.runtime.RunOutcome;
import com.adhar.kit.batch.runtime.RunTrigger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.integration.channel.QueueChannel;
import org.springframework.messaging.support.GenericMessage;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The worker's loop (design 0025 §5.10) on an in-memory channel: when it leaves, and what it does
 * with a request it claimed as the pod was told to stop.
 */
class PartitionWorkerTest {

    private final JobRepository repository = mock(JobRepository.class);
    private final JdbcPartitionTransport transport = mock(JdbcPartitionTransport.class);
    private final StaleRequestGuard guard = mock(StaleRequestGuard.class);
    private final QueueChannel channel = new QueueChannel();
    private final JobInstance instance = new JobInstance(1L, "importJob");
    private final JobParameters parameters = new JobParametersBuilder().addString("run", "x").toJobParameters();
    private final Job job = mock(Job.class);
    private final AtomicInteger sleeps = new AtomicInteger();
    private final RunContext context = new RunContext("importJob", "importer", BatchRole.WORKER, RunTrigger.MANUAL,
            "", "", "", "importer-worker-0", "", "", Optional.empty(), ZoneOffset.UTC, Optional.empty(), "", 1,
            OptionalInt.empty(), "c");

    PartitionWorkerTest() {
        when(job.getName()).thenReturn("importJob");
        when(transport.channel(anyString())).thenReturn(channel);
        when(repository.getJobInstance("importJob", parameters)).thenReturn(instance);
    }

    private PartitionWorker worker(Runnable onSleep) {
        return new PartitionWorker(transport, guard, new WorkerStopHandler(), repository,
                new DefaultListableBeanFactory(), Duration.ofMillis(1), duration -> {
                    sleeps.incrementAndGet();
                    onSleep.run();
                });
    }

    private JobExecution execution(long id, BatchStatus status) {
        JobExecution execution = new JobExecution(id, instance, parameters);
        execution.setStatus(status);
        return execution;
    }

    @Test
    @DisplayName("a worker waits through a FAILED or STOPPED execution (a restart is coming) and leaves when the run COMPLETED")
    void leavesOnlyWhenTheRunCompleted() {
        Iterator<JobExecution> lasts = List.of(execution(1L, BatchStatus.STARTED), execution(1L, BatchStatus.FAILED),
                execution(1L, BatchStatus.STOPPED), execution(2L, BatchStatus.STARTED),
                execution(2L, BatchStatus.COMPLETED)).iterator();
        JobExecution[] current = {lasts.next()};
        when(repository.getLastJobExecution(instance)).thenAnswer(invocation -> current[0]);

        RunOutcome outcome = worker(() -> current[0] = lasts.next()).consume(job, parameters, context, () -> false);

        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(outcome.exitCode()).isZero();
        assertThat(sleeps).hasValue(4);
    }

    @Test
    @DisplayName("an ABANDONED run ends the worker with exit 6, as it ends the manager")
    void abandoned() {
        when(repository.getLastJobExecution(instance)).thenReturn(execution(1L, BatchStatus.ABANDONED));

        assertThat(worker(() -> { }).consume(job, parameters, context, () -> false).exitCode()).isEqualTo(6);
    }

    @Test
    @DisplayName("a request claimed as the pod is told to stop is handed back for another worker, not started")
    void handsBackARequestClaimedWhileStopping() {
        StepExecutionRequest request = new StepExecutionRequest("importStep", 41L);
        channel.send(new GenericMessage<>(request));
        StepExecution partition = new StepExecution(41L, "importStep:partition0", execution(1L, BatchStatus.STARTED));
        when(guard.admit(any())).thenReturn(new StaleRequestGuard.Verdict(true, partition, "live"));
        AtomicInteger asked = new AtomicInteger();

        RunOutcome outcome = worker(() -> { }).consume(job, parameters, context, () -> asked.incrementAndGet() > 1);

        assertThat(outcome.exitCode()).isEqualTo(RunOutcome.SIGTERM_STATUS);
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.STOPPED);
        assertThat(channel.getQueueSize()).as("the request is back in the channel").isEqualTo(1);
        assertThat(partition.getStatus()).isEqualTo(BatchStatus.STARTING);
    }

    @Test
    @DisplayName("a stale request is discarded and a request for a step this worker lacks fails its partition at once")
    void discardsAndFailsFast() {
        StepExecutionRequest stale = new StepExecutionRequest("importStep", 40L);
        StepExecutionRequest unknownStep = new StepExecutionRequest("noSuchStep", 41L);
        channel.send(new GenericMessage<>(stale));
        channel.send(new GenericMessage<>(unknownStep));
        StepExecution partition = new StepExecution(41L, "noSuchStep:partition0", execution(1L, BatchStatus.STARTED));
        when(guard.admit(any())).thenAnswer(invocation -> invocation.getArgument(0) == stale
                ? new StaleRequestGuard.Verdict(false, null, "its job execution 0 is FAILED, not STARTED")
                : new StaleRequestGuard.Verdict(true, partition, "live"));
        when(repository.getLastJobExecution(instance)).thenReturn(execution(1L, BatchStatus.COMPLETED));

        RunOutcome outcome = worker(() -> { }).consume(job, parameters, context, () -> false);

        assertThat(outcome.exitCode()).isZero();
        assertThat(partition.getStatus()).as("left STARTING, the manager would wait for it until its timeout")
                .isEqualTo(BatchStatus.FAILED);
        assertThat(partition.getExitStatus().getExitDescription()).contains("noSuchStep");
        verify(repository).update(partition);
    }
}
