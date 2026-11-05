package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.listener.BatchRunObserver.SkipPhase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.core.step.StepExecution;

import java.util.List;

/**
 * The kit's {@link SkipListener}, attached to every chunk-oriented step by
 * {@link BatchListenerAttacher}.
 *
 * <p>It never logs an item's content: a skipped record is business data, often personal. At WARN
 * it logs the step, the phase, the item's type and the exception's class. The exception message
 * goes to DEBUG only, because Spring Batch's own messages embed the item ({@code
 * FlatFileParseException} carries the input line, a validation failure the item's
 * {@code toString()}).</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class AdharSkipListener implements SkipListener<Object, Object> {

    private final BatchRunObservers observers;

    /**
     * @param observers notified of every skip
     */
    public AdharSkipListener(List<BatchRunObserver> observers) {
        this.observers = new BatchRunObservers(observers);
    }

    @Override
    public void onSkipInRead(Throwable t) {
        skipped(SkipPhase.READ, null, t);
    }

    @Override
    public void onSkipInProcess(Object item, Throwable t) {
        skipped(SkipPhase.PROCESS, item, t);
    }

    @Override
    public void onSkipInWrite(Object item, Throwable t) {
        skipped(SkipPhase.WRITE, item, t);
    }

    private void skipped(SkipPhase phase, Object item, Throwable error) {
        StepExecution step = currentStep();
        String stepName = step != null ? step.getStepName() : "unknown";
        String itemType = item != null ? item.getClass().getName() : "-";
        String errorType = error != null ? error.getClass().getName() : "-";
        log.warn("Skipped an item in {} of step [{}] (item type {}): {}", phase.label(), stepName, itemType, errorType);
        if (log.isDebugEnabled() && error != null) {
            log.debug("Skip in {} of step [{}]: {}", phase.label(), stepName, error.getMessage());
        }
        observers.notify("itemSkipped", observer -> observer.itemSkipped(step, phase, error));
    }

    private static StepExecution currentStep() {
        StepContext context = StepSynchronizationManager.getContext();
        return context != null ? context.getStepExecution() : null;
    }
}
