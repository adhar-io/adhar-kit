package com.adhar.kit.batch.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.step.StepExecution;

import java.util.concurrent.atomic.AtomicReference;

/**
 * The graceful stop of a worker pod (design 0025 §5.9): a worker that is told to terminate stops
 * only the partition it is running, by marking that step execution {@code terminateOnly}. The step
 * ends at its next chunk boundary with its last commit intact, and the job execution, which belongs
 * to the manager, is left alone, so the other workers finish their partitions. Stopping the job
 * instead ({@code JobOperator.stop}) would stop every partition of the run.
 *
 * <p>The partition ends {@code STOPPED}. The manager does not read that as an operator's stop:
 * nobody marked the job execution {@code STOPPING}, so it fails the run as retryable and a restart
 * re-runs that partition from its last commit ({@link PartitionOutcomeAggregator}).</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class WorkerStopHandler {

    private final AtomicReference<StepExecution> current = new AtomicReference<>();
    private volatile boolean stopRequested;

    /**
     * @param partition the partition this worker is about to execute: the very object the step
     *                  executes, whose {@code terminateOnly} flag the step reads at every chunk boundary
     */
    public void serving(StepExecution partition) {
        current.set(partition);
        if (stopRequested) {
            terminate(partition);
        }
    }

    /**
     * @param partition a partition this worker has finished executing
     */
    public void served(StepExecution partition) {
        current.compareAndSet(partition, null);
    }

    /**
     * Stops the partition being executed, if any; every later {@link #serving(StepExecution)} is
     * stopped at once.
     *
     * @return whether a partition was being executed
     */
    public boolean stop() {
        stopRequested = true;
        StepExecution partition = current.get();
        if (partition == null) {
            return false;
        }
        terminate(partition);
        return true;
    }

    /**
     * @return whether the worker has been told to stop
     */
    public boolean isStopRequested() {
        return stopRequested;
    }

    private static void terminate(StepExecution partition) {
        log.info("Stopping partition {} (step execution {}) at its next chunk boundary: this worker is "
                + "terminating; the other partitions carry on", partition.getStepName(), partition.getId());
        partition.setTerminateOnly();
    }
}
