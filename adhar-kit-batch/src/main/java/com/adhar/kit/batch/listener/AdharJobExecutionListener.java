package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.event.BatchJobFailedEvent;
import com.adhar.kit.batch.metrics.BatchMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;

/**
 * A Spring Batch {@link JobExecutionListener} that logs job execution details,
 * records execution metrics, and publishes a failure event when a job fails.
 *
 * <p>This listener provides comprehensive observability for batch job lifecycle
 * events:</p>
 * <ul>
 *   <li>Structured SLF4J logging of start, completion, and failure details</li>
 *   <li>Duration and success/failure metrics via an optional {@link BatchMetrics}</li>
 *   <li>A {@link BatchJobFailedEvent} published via an optional
 *       {@link ApplicationEventPublisher} on failure, enabling decoupled
 *       failure notifications</li>
 * </ul>
 *
 * <p>Both collaborators are optional; when {@code null} the corresponding
 * behaviour is silently skipped, so the listener remains fully functional in a
 * bare Spring Batch setup.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharJobExecutionListener implements JobExecutionListener {

    private final BatchMetrics batchMetrics;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Creates a listener with no metrics or event publishing (logging only).
     */
    public AdharJobExecutionListener() {
        this(null, null);
    }

    /**
     * Creates a listener that records metrics and publishes failure events.
     *
     * @param batchMetrics   the metrics collector, may be {@code null}
     * @param eventPublisher the application event publisher, may be {@code null}
     */
    public AdharJobExecutionListener(BatchMetrics batchMetrics, ApplicationEventPublisher eventPublisher) {
        this.batchMetrics = batchMetrics;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        var jobName = jobExecution.getJobInstance().getJobName();
        var jobId = jobExecution.getId();
        var startTime = jobExecution.getStartTime();

        log.info("Job [{}] (id={}) starting at {}", jobName, jobId, startTime);
        log.info("Job [{}] parameters: {}", jobName, jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        var jobName = jobExecution.getJobInstance().getJobName();
        var jobId = jobExecution.getId();
        var startTime = jobExecution.getStartTime();
        var endTime = jobExecution.getEndTime();
        var status = jobExecution.getStatus();
        var exitCode = jobExecution.getExitStatus().getExitCode();
        var exitDescription = jobExecution.getExitStatus().getExitDescription();

        var duration = (startTime != null && endTime != null)
                ? Duration.between(startTime, endTime)
                : Duration.ZERO;
        var durationMs = duration.toMillis();
        var success = !status.isUnsuccessful();

        if (!success) {
            log.error("Job [{}] (id={}) FAILED - Status: {}, Exit Code: {}, Duration: {}ms",
                    jobName, jobId, status, exitCode, durationMs);
            if (exitDescription != null && !exitDescription.isBlank()) {
                log.error("Job [{}] failure description: {}", jobName, exitDescription);
            }
            var exceptions = jobExecution.getFailureExceptions();
            if (!exceptions.isEmpty()) {
                exceptions.forEach(ex ->
                        log.error("Job [{}] exception: {}", jobName, ex.getMessage(), ex));
            }
            publishFailureEvent(jobExecution, jobName, jobId, status, exitCode, exitDescription, durationMs);
        } else {
            log.info("Job [{}] (id={}) completed - Status: {}, Exit Code: {}, Start: {}, End: {}, Duration: {}ms",
                    jobName, jobId, status, exitCode, startTime, endTime, durationMs);
        }

        recordMetrics(jobName, durationMs, success);
    }

    private void recordMetrics(String jobName, long durationMs, boolean success) {
        if (batchMetrics == null) {
            return;
        }
        try {
            batchMetrics.recordJobExecution(jobName, durationMs, success);
        } catch (RuntimeException ex) {
            log.warn("Failed to record job metrics for [{}]: {}", jobName, ex.getMessage());
        }
    }

    private void publishFailureEvent(JobExecution jobExecution, String jobName, Long jobId,
                                     org.springframework.batch.core.BatchStatus status,
                                     String exitCode, String exitDescription, long durationMs) {
        if (eventPublisher == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(new BatchJobFailedEvent(
                    this, jobName, jobId, status, exitCode, exitDescription,
                    durationMs, jobExecution.getFailureExceptions()));
        } catch (RuntimeException ex) {
            log.warn("Failed to publish BatchJobFailedEvent for [{}]: {}", jobName, ex.getMessage());
        }
    }
}
