package com.adhar.kit.batch.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.dao.ConcurrencyFailureException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Starts a job, retrying when the JobRepository's SERIALIZABLE instance creation lost a
 * serialization conflict.
 *
 * <p>On PostgreSQL two DIFFERENT identities of one job created at the same moment conflict as
 * well (both read the job's instances, then both insert), and one of them is refused with a
 * {@link ConcurrencyFailureException}. The refused transaction left nothing behind, so it is
 * started again after a short random pause. When the conflict was another launch of the SAME
 * identity, the retry sees that launch's instance and is refused as Spring Batch refuses any
 * duplicate ({@link JobExecutionAlreadyRunningException}, {@link JobInstanceAlreadyCompleteException}).
 * Used by the Kubernetes runner and the embedded scheduler.</p>
 *
 * <p>Spring Batch creates the JobInstance and its first JobExecution in two transactions. A launch
 * of the SAME identity that arrives between them finds an instance without executions, which
 * Spring Batch reports as an {@link IllegalStateException}. That is the same conflict seen a moment
 * earlier, so it is started again too, and the retry is refused as the duplicate it is.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public final class InstanceCreationRetry {

    /** Starts tried in all: with one winner per conflict, enough for a handful of simultaneous launches. */
    public static final int MAX_ATTEMPTS = 5;

    private static final long PAUSE_MS = 50;

    /** How {@code TaskExecutorJobLauncher} words a JobInstance whose first execution is not stored yet. */
    static final String INSTANCE_WITHOUT_EXECUTION = "Cannot find any job execution for job instance";

    private InstanceCreationRetry() {
    }

    /**
     * @param jobOperator Spring Batch's operator
     * @param job         the job
     * @param parameters  its parameters
     * @return the new execution
     * @throws ConcurrencyFailureException the last of {@value #MAX_ATTEMPTS} attempts still conflicted
     * @throws IllegalStateException       the instance still has no execution after {@value #MAX_ATTEMPTS} attempts
     * @throws JobInstanceAlreadyCompleteException the instance already completed
     * @throws JobExecutionAlreadyRunningException an execution of the instance is running
     * @throws JobRestartException                 the instance cannot be restarted
     * @throws InvalidJobParametersException       the job's validator refused the parameters
     */
    public static JobExecution start(JobOperator jobOperator, Job job, JobParameters parameters)
            throws JobInstanceAlreadyCompleteException, JobExecutionAlreadyRunningException, JobRestartException,
            InvalidJobParametersException {
        for (int attempt = 1; ; attempt++) {
            try {
                return jobOperator.start(job, parameters);
            } catch (ConcurrencyFailureException ex) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw ex;
                }
                pause(job, attempt, ex);
            } catch (IllegalStateException ex) {
                if (attempt >= MAX_ATTEMPTS || ex.getMessage() == null
                        || !ex.getMessage().startsWith(INSTANCE_WITHOUT_EXECUTION)) {
                    throw ex;
                }
                pause(job, attempt, ex);
            }
        }
    }

    private static void pause(Job job, int attempt, RuntimeException conflict) {
        log.debug("Starting job [{}] conflicted with a concurrent launch (attempt {}): {}", job.getName(), attempt,
                conflict.getMessage());
        long pause = ThreadLocalRandom.current().nextLong(PAUSE_MS, PAUSE_MS * (attempt + 1));
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(pause));
    }
}
