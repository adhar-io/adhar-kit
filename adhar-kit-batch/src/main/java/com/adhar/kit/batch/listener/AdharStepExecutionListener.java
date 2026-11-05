package com.adhar.kit.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.step.StepExecution;

import java.util.List;

/**
 * The kit's {@link StepExecutionListener}, attached to every step by
 * {@link BatchListenerAttacher}: logs the step's start and its counts at the end, and notifies
 * the {@link BatchRunObserver}s.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class AdharStepExecutionListener implements StepExecutionListener {

    private final BatchRunObservers observers;

    /**
     * @param observers notified of every step start and end
     */
    public AdharStepExecutionListener(List<BatchRunObserver> observers) {
        this.observers = new BatchRunObservers(observers);
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        log.info("Step [{}] starting (step execution {})", stepExecution.getStepName(), stepExecution.getId());
        observers.notify("stepStarted", observer -> observer.stepStarted(stepExecution));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        log.info("Step [{}] {} - read {}, written {}, filtered {}, skipped {}, commits {}, rollbacks {}",
                stepExecution.getStepName(), stepExecution.getStatus(), stepExecution.getReadCount(),
                stepExecution.getWriteCount(), stepExecution.getFilterCount(), stepExecution.getSkipCount(),
                stepExecution.getCommitCount(), stepExecution.getRollbackCount());
        observers.notify("stepFinished", observer -> observer.stepFinished(stepExecution));
        // null keeps the step's own exit status.
        return null;
    }
}
