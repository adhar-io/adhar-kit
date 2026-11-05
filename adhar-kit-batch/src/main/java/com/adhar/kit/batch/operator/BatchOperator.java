package com.adhar.kit.batch.operator;

import com.adhar.kit.batch.metrics.BatchJobStats;
import com.adhar.kit.batch.params.ParameterMasking;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobExecutionNotRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Operates jobs and their executions: start, restart, stop, abandon, recover, and look up
 * history. Built on Spring Batch 6's {@link JobExecution}-based {@link JobOperator} methods; the
 * long-id methods scheduled for removal are not used.
 *
 * <p>Restart semantics belong to Spring Batch: a restart is a new execution of the same
 * JobInstance with the same identifying parameters, and the steps that completed are not run
 * again. {@link #recover(JobExecution)} marks an execution whose process died (status still
 * STARTED) as FAILED, so it can be restarted.</p>
 *
 * <p>Log lines name jobs and execution ids, never parameter values at INFO; the parameters are
 * logged masked at DEBUG ({@link ParameterMasking}).</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchOperator {

    /** How many recent JobInstances {@link #getJobStats(String)} aggregates. */
    public static final int DEFAULT_STATS_INSTANCES = 100;

    private final JobOperator jobOperator;
    private final JobRepository jobRepository;
    private final JobResolver jobResolver;

    /**
     * @param jobOperator   Spring Batch's operator
     * @param jobRepository Spring Batch's repository, for lookups
     * @param jobResolver   finds jobs by name for {@link #start(String, JobParameters)}
     */
    public BatchOperator(JobOperator jobOperator, JobRepository jobRepository, JobResolver jobResolver) {
        this.jobOperator = jobOperator;
        this.jobRepository = jobRepository;
        this.jobResolver = jobResolver;
    }

    /**
     * Starts a job; if the JobInstance of these identifying parameters failed or stopped before,
     * this restarts it.
     *
     * @param job        the job
     * @param parameters its parameters
     * @return the new execution (already ended when the operator's task executor is synchronous)
     * @throws JobInstanceAlreadyCompleteException the instance already completed
     * @throws JobExecutionAlreadyRunningException an execution of the instance is running
     * @throws JobRestartException                 the instance cannot be restarted
     * @throws InvalidJobParametersException       the job's validator refused the parameters
     */
    public JobExecution start(Job job, JobParameters parameters) throws JobInstanceAlreadyCompleteException,
            JobExecutionAlreadyRunningException, JobRestartException, InvalidJobParametersException {
        Assert.notNull(job, "job must not be null");
        log.info("Starting job [{}]", job.getName());
        logParameters(job.getName(), parameters);
        return jobOperator.start(job, parameters);
    }

    /**
     * Starts a job by name.
     *
     * @param jobName    the job's bean name or name
     * @param parameters its parameters
     * @return the new execution
     * @throws IllegalArgumentException when there is no such job
     * @throws JobInstanceAlreadyCompleteException the instance already completed
     * @throws JobExecutionAlreadyRunningException an execution of the instance is running
     * @throws JobRestartException                 the instance cannot be restarted
     * @throws InvalidJobParametersException       the job's validator refused the parameters
     * @see #start(Job, JobParameters)
     */
    public JobExecution start(String jobName, JobParameters parameters) throws JobInstanceAlreadyCompleteException,
            JobExecutionAlreadyRunningException, JobRestartException, InvalidJobParametersException {
        return start(jobResolver.resolve(jobName), parameters);
    }

    /**
     * Restarts a FAILED or STOPPED execution: a new execution of the same JobInstance that
     * resumes from the last committed chunk.
     *
     * @param execution the execution to restart
     * @return the new execution
     * @throws JobRestartException when it cannot be restarted (running, completed, abandoned)
     */
    public JobExecution restart(JobExecution execution) throws JobRestartException {
        Assert.notNull(execution, "execution must not be null");
        log.info("Restarting execution {} of job [{}]", execution.getId(), execution.getJobInstance().getJobName());
        return jobOperator.restart(execution);
    }

    /**
     * Asks a running execution to stop: it stops at its next chunk boundary and records STOPPED.
     *
     * @param execution the running execution
     * @return {@code true} once the request is recorded
     * @throws JobExecutionNotRunningException when it is not running
     */
    public boolean stop(JobExecution execution) throws JobExecutionNotRunningException {
        Assert.notNull(execution, "execution must not be null");
        log.info("Stopping execution {} of job [{}]", execution.getId(), execution.getJobInstance().getJobName());
        return jobOperator.stop(execution);
    }

    /**
     * Abandons a STOPPED or FAILED execution: its instance can no longer be restarted, and
     * running it again needs new identifying parameters.
     *
     * @param execution the execution
     * @return the abandoned execution
     * @throws JobExecutionAlreadyRunningException when it is still running
     */
    public JobExecution abandon(JobExecution execution) throws JobExecutionAlreadyRunningException {
        Assert.notNull(execution, "execution must not be null");
        log.info("Abandoning execution {} of job [{}]", execution.getId(), execution.getJobInstance().getJobName());
        return jobOperator.abandon(execution);
    }

    /**
     * Recovers an execution whose process died while it ran (its status is still STARTED or
     * STOPPING): marks its running steps and itself FAILED, so it can be restarted. Idempotent.
     * Only call it when the process that ran it is known to be gone.
     *
     * @param execution the orphaned execution
     * @return the recovered execution (unchanged when already complete, abandoned or recovered)
     */
    public JobExecution recover(JobExecution execution) {
        Assert.notNull(execution, "execution must not be null");
        log.info("Recovering execution {} of job [{}]", execution.getId(), execution.getJobInstance().getJobName());
        return jobOperator.recover(execution);
    }

    /**
     * @param executionId an execution id
     * @return the execution, with its step executions
     */
    public Optional<JobExecution> findExecution(long executionId) {
        return Optional.ofNullable(jobRepository.getJobExecution(executionId));
    }

    /**
     * Every execution of the JobInstance that these identifying parameters denote.
     *
     * @param jobName    the job's name
     * @param parameters the parameters (only the identifying ones count)
     * @return the executions, most recent first; empty when the instance does not exist
     */
    public List<JobExecution> findExecutions(String jobName, JobParameters parameters) {
        JobInstance instance = jobRepository.getJobInstance(jobName, parameters);
        return instance == null ? List.of() : jobRepository.getJobExecutions(instance);
    }

    /**
     * @param job        the job
     * @param parameters the parameters (only the identifying ones count)
     * @return the executions of the instance, most recent first
     * @see #findExecutions(String, JobParameters)
     */
    public List<JobExecution> findExecutions(Job job, JobParameters parameters) {
        return findExecutions(job.getName(), parameters);
    }

    /**
     * @param jobName    the job's name
     * @param parameters the parameters (only the identifying ones count)
     * @return the latest execution of the instance they denote
     */
    public Optional<JobExecution> findLastExecution(String jobName, JobParameters parameters) {
        JobInstance instance = jobRepository.getJobInstance(jobName, parameters);
        return instance == null ? Optional.empty() : Optional.ofNullable(jobRepository.getLastJobExecution(instance));
    }

    /**
     * @param jobName the job's name
     * @return its executions that are STARTING, STARTED or STOPPING
     */
    public Set<JobExecution> getRunningExecutions(String jobName) {
        return jobRepository.findRunningJobExecutions(jobName);
    }

    /**
     * Statistics of a job from the JobRepository: the executions of its
     * {@value #DEFAULT_STATS_INSTANCES} most recent JobInstances.
     *
     * @param jobName the job's name
     * @return the statistics; zeros when the job never ran
     */
    public BatchJobStats getJobStats(String jobName) {
        return getJobStats(jobName, DEFAULT_STATS_INSTANCES);
    }

    /**
     * Statistics of a job from the JobRepository.
     *
     * @param jobName      the job's name
     * @param maxInstances how many of its most recent JobInstances to aggregate
     * @return executions in total, COMPLETED ones, unsuccessful ones (FAILED, ABANDONED,
     *         UNKNOWN), the mean duration of the ended ones and the latest start
     */
    public BatchJobStats getJobStats(String jobName, int maxInstances) {
        Assert.isTrue(maxInstances > 0, "maxInstances must be positive");
        long total = 0;
        long completed = 0;
        long unsuccessful = 0;
        long ended = 0;
        long durationMs = 0;
        LocalDateTime latest = null;
        for (JobInstance instance : jobRepository.getJobInstances(jobName, 0, maxInstances)) {
            for (JobExecution execution : jobRepository.getJobExecutions(instance)) {
                total++;
                BatchStatus status = execution.getStatus();
                if (status == BatchStatus.COMPLETED) {
                    completed++;
                } else if (status.isUnsuccessful()) {
                    unsuccessful++;
                }
                LocalDateTime start = execution.getStartTime();
                LocalDateTime end = execution.getEndTime();
                if (start != null && end != null) {
                    ended++;
                    durationMs += Duration.between(start, end).toMillis();
                }
                LocalDateTime when = start != null ? start : execution.getCreateTime();
                if (latest == null || when.isAfter(latest)) {
                    latest = when;
                }
            }
        }
        return new BatchJobStats(total, completed, unsuccessful, ended > 0 ? (double) durationMs / ended : 0.0,
                latest == null ? null : latest.atZone(ZoneId.systemDefault()).toInstant());
    }

    private static void logParameters(String jobName, JobParameters parameters) {
        if (log.isDebugEnabled()) {
            log.debug("Job [{}] parameters {}", jobName, ParameterMasking.describe(parameters));
        }
    }
}
