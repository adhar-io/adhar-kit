package com.adhar.kit.batch.listener;

import com.adhar.kit.batch.event.BatchJobFailedEvent;
import com.adhar.kit.batch.params.ParameterMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.util.List;

/**
 * The kit's {@link JobExecutionListener}, attached to every job by {@link BatchListenerAttacher}.
 *
 * <p>It logs the start and the end of a run without its parameters (they are logged masked, at
 * DEBUG: see {@link ParameterMasking}), publishes a {@link BatchJobFailedEvent} when a run ends
 * unsuccessfully, and notifies the {@link BatchRunObserver}s.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class AdharJobExecutionListener implements JobExecutionListener {

    private final ApplicationEventPublisher eventPublisher;
    private final BatchRunObservers observers;

    /**
     * @param eventPublisher publishes {@link BatchJobFailedEvent}, may be {@code null}
     * @param observers      notified of every start and end
     */
    public AdharJobExecutionListener(ApplicationEventPublisher eventPublisher, List<BatchRunObserver> observers) {
        this.eventPublisher = eventPublisher;
        this.observers = new BatchRunObservers(observers);
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        log.info("Job [{}] starting (execution {}, instance {})", jobName, jobExecution.getId(),
                jobExecution.getJobInstance().getInstanceId());
        if (log.isDebugEnabled()) {
            log.debug("Job [{}] parameters {}", jobName, ParameterMasking.describe(jobExecution.getJobParameters()));
        }
        observers.notify("jobStarted", observer -> observer.jobStarted(jobExecution));
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        BatchStatus status = jobExecution.getStatus();
        long durationMs = durationMs(jobExecution);
        String exitCode = jobExecution.getExitStatus().getExitCode();
        if (status.isUnsuccessful()) {
            log.error("Job [{}] {} (execution {}, exit code {}, {} ms)", jobName, status, jobExecution.getId(),
                    exitCode, durationMs);
            for (Throwable failure : jobExecution.getAllFailureExceptions()) {
                log.error("Job [{}] failure", jobName, failure);
            }
            publishFailure(jobExecution, jobName, status, exitCode, durationMs);
        } else {
            log.info("Job [{}] {} (execution {}, exit code {}, {} ms)", jobName, status, jobExecution.getId(),
                    exitCode, durationMs);
        }
        observers.notify("jobFinished", observer -> observer.jobFinished(jobExecution));
    }

    private static long durationMs(JobExecution execution) {
        if (execution.getStartTime() == null || execution.getEndTime() == null) {
            return 0;
        }
        return Duration.between(execution.getStartTime(), execution.getEndTime()).toMillis();
    }

    private void publishFailure(JobExecution jobExecution, String jobName, BatchStatus status, String exitCode,
                                long durationMs) {
        if (eventPublisher == null) {
            return;
        }
        try {
            eventPublisher.publishEvent(new BatchJobFailedEvent(this, jobName, jobExecution.getId(), status,
                    exitCode, jobExecution.getExitStatus().getExitDescription(), durationMs,
                    jobExecution.getAllFailureExceptions()));
        } catch (RuntimeException ex) {
            log.warn("Could not publish BatchJobFailedEvent for job [{}]: {}", jobName, ex.getMessage());
        }
    }
}
