package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.partition.WorkerStopHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Stops a Kubernetes run gracefully when the pod is told to terminate (design 0025 §5.9).
 *
 * <p>SIGTERM closes the application context; this lifecycle stops in the earliest phase, before
 * the DataSource or anything the job writes to is gone. It asks Spring Batch to stop the live
 * execution ({@code JobOperator.stop}: the step ends at its next chunk boundary and the execution
 * is recorded STOPPED), then waits up to {@code adhar.batch.shutdown.timeout} for the runner to
 * finish, which includes writing the termination summary. The JVM then exits with its own signal
 * status; nothing calls {@code System.exit} during the shutdown, which would block.</p>
 *
 * <p>A worker pod of a remote-partitioned job has no execution of its own: it stops only the
 * partition it is running ({@link WorkerStopHandler}), so the other workers finish theirs and the
 * manager decides the run.</p>
 *
 * <p>Spring Batch's own {@code JobExecutionShutdownHook} only signals the stop and returns,
 * racing the DataSource's close; the kit does not use it.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class GracefulStopLifecycle implements SmartLifecycle {

    private final ObjectProvider<JobOperator> jobOperator;
    private final Duration timeout;
    private final WorkerStopHandler workerStop;
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicReference<JobExecution> execution = new AtomicReference<>();
    private volatile CountDownLatch finished;

    /**
     * @param jobOperator stops the execution; resolved only when needed
     * @param timeout     {@code adhar.batch.shutdown.timeout}
     */
    public GracefulStopLifecycle(ObjectProvider<JobOperator> jobOperator, Duration timeout) {
        this(jobOperator, timeout, null);
    }

    /**
     * @param jobOperator stops the execution; resolved only when needed
     * @param timeout     {@code adhar.batch.shutdown.timeout}
     * @param workerStop  stops a worker's current partition; {@code null} without remote partitioning
     */
    public GracefulStopLifecycle(ObjectProvider<JobOperator> jobOperator, Duration timeout,
                                 WorkerStopHandler workerStop) {
        this.jobOperator = jobOperator;
        this.timeout = timeout;
        this.workerStop = workerStop;
    }

    /**
     * The runner has begun: a shutdown from now on waits for it.
     */
    public void runStarted() {
        finished = new CountDownLatch(1);
    }

    /**
     * @param started the live execution the runner is running
     */
    public void executionStarted(JobExecution started) {
        execution.set(started);
        if (stopping.get()) {
            stopExecution(started);
        }
    }

    /**
     * @param ended an execution that has ended
     */
    public void executionEnded(JobExecution ended) {
        execution.compareAndSet(ended, null);
    }

    /**
     * The runner has finished, summary written: a waiting shutdown may proceed.
     */
    public void runFinished() {
        CountDownLatch latch = finished;
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * @return whether the pod is shutting down (SIGTERM, or the context closing)
     */
    public boolean isStopping() {
        return stopping.get();
    }

    @Override
    public void start() {
        running.set(true);
    }

    @Override
    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        stopping.set(true);
        CountDownLatch latch = finished;
        if (latch == null || latch.getCount() == 0) {
            return;
        }
        JobExecution current = execution.get();
        if (current != null) {
            stopExecution(current);
        } else if (workerStop != null) {
            workerStop.stop();
        }
        try {
            if (!latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("The run did not stop within adhar.batch.shutdown.timeout ({}); the pod will be killed and "
                        + "the next run recovers the execution", timeout);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void stopExecution(JobExecution current) {
        log.info("Stopping execution {} of job [{}] at its next chunk boundary: the pod is terminating",
                current.getId(), current.getJobInstance().getJobName());
        try {
            jobOperator.getObject().stop(current);
        } catch (JobExecutionNotRunningException ex) {
            log.debug("Execution {} had already ended", current.getId());
        } catch (RuntimeException ex) {
            log.warn("Could not stop execution {}: {}", current.getId(), ex.getMessage());
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * @return {@link Integer#MAX_VALUE}: the first phase to stop
     */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
