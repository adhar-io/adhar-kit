package com.adhar.kit.batch.event;

import org.springframework.batch.core.BatchStatus;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Spring {@link ApplicationEvent} published when a batch job execution fails.
 *
 * <p>Published by {@link com.adhar.kit.batch.listener.AdharJobExecutionListener}
 * from its {@code afterJob} callback whenever the job execution ends in an
 * unsuccessful {@link BatchStatus}. Application code can consume this event with
 * a standard {@code @EventListener} method to trigger failure notifications
 * (email, Slack, paging, etc.) without coupling to the batch listener.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @EventListener
 * public void onBatchFailure(BatchJobFailedEvent event) {
 *     alertService.notify("Batch job " + event.getJobName() + " failed: " + event.getExitCode());
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class BatchJobFailedEvent extends ApplicationEvent {

    private final String jobName;
    private final Long jobExecutionId;
    private final BatchStatus status;
    private final String exitCode;
    private final String exitDescription;
    private final long durationMs;
    private final List<Throwable> failureExceptions;

    /**
     * Creates a new {@code BatchJobFailedEvent}.
     *
     * @param source            the object on which the event initially occurred (never {@code null})
     * @param jobName           the name of the failed job
     * @param jobExecutionId    the id of the failed job execution
     * @param status            the terminal batch status
     * @param exitCode          the exit code of the job execution
     * @param exitDescription   the exit description, may be {@code null}
     * @param durationMs         the execution duration in milliseconds
     * @param failureExceptions the exceptions that caused the failure (never {@code null})
     */
    public BatchJobFailedEvent(Object source,
                               String jobName,
                               Long jobExecutionId,
                               BatchStatus status,
                               String exitCode,
                               String exitDescription,
                               long durationMs,
                               List<Throwable> failureExceptions) {
        super(source);
        this.jobName = jobName;
        this.jobExecutionId = jobExecutionId;
        this.status = status;
        this.exitCode = exitCode;
        this.exitDescription = exitDescription;
        this.durationMs = durationMs;
        this.failureExceptions = failureExceptions == null ? List.of() : List.copyOf(failureExceptions);
    }

    /**
     * @return the name of the failed job
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * @return the id of the failed job execution
     */
    public Long getJobExecutionId() {
        return jobExecutionId;
    }

    /**
     * @return the terminal batch status
     */
    public BatchStatus getStatus() {
        return status;
    }

    /**
     * @return the exit code of the job execution
     */
    public String getExitCode() {
        return exitCode;
    }

    /**
     * @return the exit description, may be {@code null}
     */
    public String getExitDescription() {
        return exitDescription;
    }

    /**
     * @return the execution duration in milliseconds
     */
    public long getDurationMs() {
        return durationMs;
    }

    /**
     * @return an unmodifiable list of the exceptions that caused the failure
     */
    public List<Throwable> getFailureExceptions() {
        return failureExceptions;
    }
}
