package com.adhar.kit.batch.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A Spring Batch {@link JobExecutionListener} that logs job execution details
 * including name, timing, status, and exit code using SLF4J.
 *
 * <p>This listener provides comprehensive logging for batch job lifecycle events,
 * making it easier to monitor and debug batch processing in production environments.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class AdharJobExecutionListener implements JobExecutionListener {

    @Override
    public void beforeJob(JobExecution jobExecution) {
        var jobName = jobExecution.getJobInstance().getJobName();
        var jobId = jobExecution.getJobId();
        var startTime = jobExecution.getStartTime();

        log.info("Job [{}] (id={}) starting at {}", jobName, jobId, startTime);
        log.info("Job [{}] parameters: {}", jobName, jobExecution.getJobParameters());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        var jobName = jobExecution.getJobInstance().getJobName();
        var jobId = jobExecution.getJobId();
        var startTime = jobExecution.getStartTime();
        var endTime = jobExecution.getEndTime();
        var status = jobExecution.getStatus();
        var exitCode = jobExecution.getExitStatus().getExitCode();
        var exitDescription = jobExecution.getExitStatus().getExitDescription();

        var duration = (startTime != null && endTime != null)
                ? Duration.between(startTime, endTime)
                : Duration.ZERO;

        if (status.isUnsuccessful()) {
            log.error("Job [{}] (id={}) FAILED - Status: {}, Exit Code: {}, Duration: {}ms",
                    jobName, jobId, status, exitCode, duration.toMillis());
            if (exitDescription != null && !exitDescription.isBlank()) {
                log.error("Job [{}] failure description: {}", jobName, exitDescription);
            }
            var exceptions = jobExecution.getAllFailureExceptions();
            if (!exceptions.isEmpty()) {
                exceptions.forEach(ex ->
                        log.error("Job [{}] exception: {}", jobName, ex.getMessage(), ex));
            }
        } else {
            log.info("Job [{}] (id={}) completed - Status: {}, Exit Code: {}, Start: {}, End: {}, Duration: {}ms",
                    jobName, jobId, status, exitCode, startTime, endTime, duration.toMillis());
        }
    }
}
