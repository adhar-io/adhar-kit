package com.adhar.kit.batch.listener;

import com.adhar.adharkit.logging.batch.BatchJobLogger;
import com.adhar.adharkit.logging.batch.BatchJobRun;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drives {@code adhar-kit-logging}'s {@link BatchJobLogger} from every job the kit observes, so
 * each run emits the platform's structured BATCH log events (started, step transitions,
 * progress with throughput, completed or failed) without code in the job.
 *
 * <p>Active only when {@code adhar-kit-logging} is on the classpath and provides the
 * {@link BatchJobLogger} bean. Items are counted as processed when their chunk commits.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class BatchJobLoggerObserver implements BatchRunObserver {

    private final BatchJobLogger batchJobLogger;
    private final Map<Long, BatchJobRun> runs = new ConcurrentHashMap<>();

    /**
     * @param batchJobLogger the logging module's batch logger
     */
    public BatchJobLoggerObserver(BatchJobLogger batchJobLogger) {
        this.batchJobLogger = batchJobLogger;
    }

    @Override
    public void jobStarted(JobExecution execution) {
        runs.put(execution.getId(), batchJobLogger.startJob(execution.getJobInstance().getJobName()));
    }

    @Override
    public void stepStarted(StepExecution execution) {
        BatchJobRun run = runs.get(execution.getJobExecutionId());
        if (run != null) {
            run.startStep(execution.getStepName());
        }
    }

    @Override
    public void chunkCommitted(StepExecution execution, long items) {
        BatchJobRun run = runs.get(execution.getJobExecutionId());
        if (run != null) {
            for (long i = 0; i < items; i++) {
                run.itemProcessed();
            }
        }
    }

    @Override
    public void itemSkipped(StepExecution execution, SkipPhase phase, Throwable error) {
        BatchJobRun run = execution != null ? runs.get(execution.getJobExecutionId()) : null;
        if (run != null) {
            run.itemSkipped();
        }
    }

    @Override
    public void stepFinished(StepExecution execution) {
        BatchJobRun run = runs.get(execution.getJobExecutionId());
        if (run != null) {
            run.completeStep();
        }
    }

    @Override
    public void jobFinished(JobExecution execution) {
        BatchJobRun run = runs.remove(execution.getId());
        if (run == null) {
            return;
        }
        if (execution.getStatus() == BatchStatus.COMPLETED) {
            run.complete();
        } else {
            List<Throwable> failures = execution.getAllFailureExceptions();
            run.fail(failures.isEmpty()
                    ? new IllegalStateException("Job ended " + execution.getStatus() + " with exit code "
                            + execution.getExitStatus().getExitCode())
                    : failures.getFirst());
        }
    }

    /**
     * @return the number of runs currently tracked (for diagnostics and tests)
     */
    int trackedRuns() {
        return runs.size();
    }
}
