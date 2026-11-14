package com.adhar.kit.batch.partition;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.integration.partition.StepExecutionRequest;
import org.springframework.dao.EmptyResultDataAccessException;

/**
 * The worker half of the stale-request guard (design 0025 §5.10, F20): a partition request runs only
 * while the attempt that sent it is the live one. The manager half clears the run's message group
 * whenever it starts or restarts an execution ({@link ManagerPartitionGuard}); a request that was
 * claimed before that, or that slipped in from an attempt a restart has since recovered, is
 * discarded here instead of executed.
 *
 * <p>A request is admitted only when its partition's step execution exists and has not started,
 * its job execution is {@code STARTED}, and that job execution is the latest of its JobInstance.
 * Anything else belongs to an attempt that has failed, stopped, been recovered or been superseded;
 * the repository already knows which partitions still need to run, and the live manager has asked
 * for those itself.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class StaleRequestGuard {

    private final JobRepository jobRepository;

    /**
     * @param jobRepository the shared JobRepository
     */
    public StaleRequestGuard(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * @param request a request a worker has claimed
     * @return whether to execute it, with the partition and the reason
     */
    public Verdict admit(StepExecutionRequest request) {
        StepExecution partition = stepExecution(jobRepository, request.getStepExecutionId());
        if (partition == null) {
            return Verdict.discard(null, "its step execution " + request.getStepExecutionId() + " does not exist");
        }
        JobExecution execution = partition.getJobExecution();
        if (execution.getStatus() != BatchStatus.STARTED) {
            return Verdict.discard(partition, "its job execution " + execution.getId() + " is "
                    + execution.getStatus() + ", not STARTED");
        }
        JobExecution latest = jobRepository.getLastJobExecution(execution.getJobInstance());
        if (latest != null && latest.getId() != execution.getId()) {
            return Verdict.discard(partition, "its job execution " + execution.getId() + " was superseded by "
                    + latest.getId());
        }
        if (partition.getStatus() != BatchStatus.STARTING) {
            return Verdict.discard(partition, "its partition " + partition.getStepName() + " is already "
                    + partition.getStatus());
        }
        return new Verdict(true, partition, "live");
    }

    /**
     * @param jobRepository   the JobRepository
     * @param stepExecutionId a step execution's id
     * @return it, or {@code null} when there is none (the JDBC repository throws for an unknown id)
     */
    static StepExecution stepExecution(JobRepository jobRepository, long stepExecutionId) {
        try {
            return jobRepository.getStepExecution(stepExecutionId);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    /**
     * @param admitted  whether the request may run
     * @param partition the partition's step execution, {@code null} when it does not exist
     * @param reason    why, for the log
     */
    public record Verdict(boolean admitted, StepExecution partition, String reason) {

        static Verdict discard(StepExecution partition, String reason) {
            return new Verdict(false, partition, reason);
        }
    }
}
