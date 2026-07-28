package com.adhar.kit.batch.operator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.launch.NoSuchJobException;
import org.springframework.batch.core.launch.NoSuchJobExecutionException;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.repository.JobRepository;

import java.util.Set;

/**
 * Operational facade over Spring Batch's {@link JobOperator} and
 * {@link JobRepository} for managing running and stopped job executions.
 *
 * <p>Provides a small, exception-friendly API for the common lifecycle
 * operations required to operate batch jobs in production:</p>
 * <ul>
 *   <li>{@link #restart(long)} - restart a failed or stopped execution</li>
 *   <li>{@link #stop(long)} - signal a running execution to stop</li>
 *   <li>{@link #abandon(long)} - mark a stopped execution as abandoned</li>
 *   <li>{@link #getRunningExecutions(String)} - list running execution ids</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class BatchOperator {

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;

    /**
     * Creates a new {@code BatchOperator}.
     *
     * @param jobOperator   the Spring Batch job operator
     * @param jobRepository the Spring Batch job repository for execution lookups
     */
    public BatchOperator(JobOperator jobOperator, JobRepository jobRepository) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
    }

    /**
     * Restarts a previously failed or stopped job execution.
     *
     * @param executionId the id of the job execution to restart
     * @return the id of the new job execution
     * @throws NoSuchJobExecutionException          if the execution id is unknown
     * @throws NoSuchJobException                   if the job is no longer registered
     * @throws JobInstanceAlreadyCompleteException  if the instance already completed successfully
     * @throws JobRestartException                  if the job cannot be restarted
     * @throws InvalidJobParametersException        if the stored job parameters are invalid
     */
    public Long restart(long executionId)
            throws NoSuchJobExecutionException, NoSuchJobException,
            JobInstanceAlreadyCompleteException, JobRestartException, InvalidJobParametersException {
        log.info("Restarting job execution [{}]", executionId);
        var newId = jobOperator.restart(executionId);
        log.info("Restarted job execution [{}] as new execution [{}]", executionId, newId);
        return newId;
    }

    /**
     * Signals a running job execution to stop at the next chunk boundary.
     *
     * @param executionId the id of the job execution to stop
     * @return {@code true} if the stop signal was successfully sent
     * @throws NoSuchJobExecutionException     if the execution id is unknown
     * @throws JobExecutionNotRunningException if the execution is not running
     */
    public boolean stop(long executionId)
            throws NoSuchJobExecutionException, JobExecutionNotRunningException {
        log.info("Stopping job execution [{}]", executionId);
        return jobOperator.stop(executionId);
    }

    /**
     * Marks a stopped or failed job execution as abandoned so it is no longer
     * considered for restart.
     *
     * @param executionId the id of the job execution to abandon
     * @return the abandoned {@link JobExecution}
     * @throws NoSuchJobExecutionException          if the execution id is unknown
     * @throws JobExecutionAlreadyRunningException  if the execution is still running
     */
    public JobExecution abandon(long executionId)
            throws NoSuchJobExecutionException, JobExecutionAlreadyRunningException {
        log.info("Abandoning job execution [{}]", executionId);
        return jobOperator.abandon(executionId);
    }

    /**
     * Returns the ids of the currently running executions for the given job.
     *
     * @param jobName the job name
     * @return the set of running execution ids
     * @throws NoSuchJobException if the job is not known to the operator
     */
    public Set<Long> getRunningExecutions(String jobName) throws NoSuchJobException {
        return jobOperator.getRunningExecutions(jobName);
    }

    /**
     * Looks up a job execution by id via the job repository.
     *
     * @param executionId the execution id
     * @return the {@link JobExecution}, or {@code null} if none exists
     */
    public JobExecution getJobExecution(long executionId) {
        return jobRepository.getJobExecution(executionId);
    }
}
