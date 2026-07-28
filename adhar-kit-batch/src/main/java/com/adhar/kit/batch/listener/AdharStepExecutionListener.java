package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.metrics.BatchMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

/**
 * A Spring Batch {@link StepExecutionListener} that logs step execution details
 * and records per-step read/write/skip counts to an optional {@link BatchMetrics}.
 *
 * <p>The metrics collector is optional; when {@code null} the recording is
 * silently skipped so the listener works in a bare Spring Batch setup.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharStepExecutionListener implements StepExecutionListener {

    private final BatchMetrics batchMetrics;

    /**
     * Creates a listener that only logs step execution (no metrics).
     */
    public AdharStepExecutionListener() {
        this(null);
    }

    /**
     * Creates a listener that records step metrics.
     *
     * @param batchMetrics the metrics collector, may be {@code null}
     */
    public AdharStepExecutionListener(BatchMetrics batchMetrics) {
        this.batchMetrics = batchMetrics;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Step [{}] starting", stepExecution.getStepName());
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        var stepName = stepExecution.getStepName();
        var readCount = stepExecution.getReadCount();
        var writeCount = stepExecution.getWriteCount();
        var skipCount = stepExecution.getSkipCount();

        log.info("Step [{}] completed - Status: {}, Reads: {}, Writes: {}, Skips: {}",
                stepName, stepExecution.getStatus(), readCount, writeCount, skipCount);

        if (batchMetrics != null) {
            try {
                batchMetrics.recordStepExecution(stepName, readCount, writeCount, skipCount);
            } catch (RuntimeException ex) {
                log.warn("Failed to record step metrics for [{}]: {}", stepName, ex.getMessage());
            }
        }

        // Return null to leave the step's own exit status unchanged.
        return null;
    }
}
