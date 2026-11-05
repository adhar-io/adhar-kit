package com.adhar.kit.batch.listener;

import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

/**
 * Receives the lifecycle of every job and step the kit's listeners are attached to.
 *
 * <p>The kit attaches one set of Spring Batch listeners to every job and step
 * ({@link BatchListenerAttacher}); they fan out to the observers, which keep metrics, drive the
 * structured BATCH log events and so on. An observer that throws is logged and skipped: it never
 * fails a run.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public interface BatchRunObserver {

    /**
     * @param execution the execution that is starting
     */
    default void jobStarted(JobExecution execution) {
    }

    /**
     * @param execution the execution that has ended (any status)
     */
    default void jobFinished(JobExecution execution) {
    }

    /**
     * @param execution the step execution that is starting
     */
    default void stepStarted(StepExecution execution) {
    }

    /**
     * @param execution the step execution that has ended (any status)
     */
    default void stepFinished(StepExecution execution) {
    }

    /**
     * A chunk's transaction committed: its items and the step's progress are durable.
     *
     * @param execution the step execution
     * @param items     the number of items the chunk wrote
     */
    default void chunkCommitted(StepExecution execution, long items) {
    }

    /**
     * A fault-tolerant step skipped an item.
     *
     * @param execution the step execution, {@code null} when not on the step's thread
     * @param phase     where the item failed
     * @param error     why it was skipped
     */
    default void itemSkipped(StepExecution execution, SkipPhase phase, Throwable error) {
    }

    /**
     * Where a skipped item failed.
     */
    enum SkipPhase {
        /** Reading the item failed; there is no item. */
        READ,
        /** Processing the item failed. */
        PROCESS,
        /** Writing the item failed. */
        WRITE;

        /**
         * @return the lower-case name used in logs and the dead-letter table
         */
        public String label() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
