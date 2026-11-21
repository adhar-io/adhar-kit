package com.adhar.kit.batch.ops;

import com.adhar.kit.batch.runtime.ExecutionLease;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.util.Assert;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Housekeeping of a job's JobRepository metadata (design 0025 D4, A3.5). Without it, one run a day
 * leaves its instance, execution, parameters, contexts and steps in the application's database
 * forever.
 *
 * <p>An instance is deleted only when ALL of these hold:</p>
 * <ul>
 *   <li>it is not among the newest {@code keepLast} instances of the job;</li>
 *   <li>it is not the newest instance whose last execution FAILED or STOPPED, on a restartable
 *       job: that one is what the next restart resumes, whatever its age;</li>
 *   <li>none of its executions, nor any of their steps, is still running;</li>
 *   <li>it has executions, and the latest end (or update) among them is older than {@code days}.
 *       An instance without executions is being created by a launch right now.</li>
 * </ul>
 *
 * <p>Deleting goes through {@link JobRepository#deleteJobInstance(JobInstance)}, so the parameters,
 * contexts and step executions go with it, and the lease rows of its executions are released.
 * The dead letters stay: they are kept by job name for inspection and replay, not by execution.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class RepositoryHousekeeping {

    /** How many instances are read per page. */
    static final int PAGE = 500;

    private final JobRepository jobRepository;
    private final ExecutionLease lease;
    private final Recorder recorder;
    private final Clock clock;

    /**
     * @param jobRepository the JobRepository
     * @param lease         the execution leases, released with their executions
     * @param recorder      counts each pass
     * @param clock         the clock {@code days} is measured against
     */
    public RepositoryHousekeeping(JobRepository jobRepository, ExecutionLease lease, Recorder recorder, Clock clock) {
        this.jobRepository = jobRepository;
        this.lease = lease;
        this.recorder = recorder;
        this.clock = clock;
    }

    /**
     * Deletes what the retention no longer keeps, and counts the pass. A failure part-way is
     * counted as failed and rethrown; what was deleted before it stays deleted.
     *
     * @param jobName     the job
     * @param restartable whether the job can be restarted (a non-restartable job's FAILED instance
     *                    is not kept for a restart that cannot happen)
     * @param days        metadata older than this many days goes
     * @param keepLast    the newest instances that always stay
     * @return what was examined, kept and deleted
     */
    public Result clean(String jobName, boolean restartable, int days, int keepLast) {
        Assert.isTrue(days >= 1, "days must be at least 1");
        Assert.isTrue(keepLast >= 0, "keepLast must not be negative");
        Instant cutoff = clock.instant().minus(Duration.ofDays(days));
        int examined = 0;
        int deleted = 0;
        try {
            boolean restartKept = false;
            for (JobInstance instance : instances(jobName)) {
                List<JobExecution> executions = jobRepository.getJobExecutions(instance);
                JobExecution last = executions.stream().max(Comparator.comparingLong(JobExecution::getId)).orElse(null);
                boolean awaitsRestart = restartable && !restartKept && last != null
                        && (last.getStatus() == BatchStatus.FAILED || last.getStatus() == BatchStatus.STOPPED);
                restartKept |= awaitsRestart;
                boolean keep = examined < keepLast || awaitsRestart || executions.isEmpty()
                        || executions.stream().anyMatch(RepositoryHousekeeping::running)
                        || !lastActivity(executions).isBefore(cutoff);
                examined++;
                if (keep) {
                    continue;
                }
                for (JobExecution execution : executions) {
                    lease.release(execution.getId());
                }
                jobRepository.deleteJobInstance(instance);
                deleted++;
            }
        } catch (RuntimeException ex) {
            recorder.record(jobName, false, deleted);
            throw ex;
        }
        recorder.record(jobName, true, deleted);
        Result result = new Result(examined, examined - deleted, deleted);
        if (deleted > 0) {
            log.info("Housekeeping of job [{}]: deleted {} of {} instances (older than {} days, keeping the newest {})",
                    jobName, deleted, examined, days, keepLast);
        }
        return result;
    }

    /** Every instance of the job, newest first, read before anything is deleted (paging would shift). */
    private List<JobInstance> instances(String jobName) {
        List<JobInstance> all = new ArrayList<>();
        for (int start = 0; ; start += PAGE) {
            List<JobInstance> page = jobRepository.getJobInstances(jobName, start, PAGE);
            all.addAll(page);
            if (page.size() < PAGE) {
                break;
            }
        }
        all.sort(Comparator.comparingLong(JobInstance::getInstanceId).reversed());
        return all;
    }

    private static boolean running(JobExecution execution) {
        return execution.getStatus().isRunning()
                || execution.getStepExecutions().stream().anyMatch(step -> step.getStatus().isRunning());
    }

    private static Instant lastActivity(List<JobExecution> executions) {
        LocalDateTime latest = null;
        for (JobExecution execution : executions) {
            LocalDateTime when = execution.getEndTime() != null ? execution.getEndTime()
                    : execution.getLastUpdated() != null ? execution.getLastUpdated() : execution.getCreateTime();
            if (latest == null || when.isAfter(latest)) {
                latest = when;
            }
        }
        // The JobRepository records local times of the JVM that wrote them (LocalDateTime.now()).
        return latest.atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * @param examined the job's instances looked at
     * @param kept     how many stay
     * @param deleted  how many were deleted
     */
    public record Result(int examined, int kept, int deleted) {
    }

    /** Counts housekeeping passes ({@code adhar.batch.housekeeping}). */
    @FunctionalInterface
    public interface Recorder {

        /**
         * @param jobName   the job
         * @param succeeded whether the pass ended without an error
         * @param deleted   how many instances it deleted
         */
        void record(String jobName, boolean succeeded, long deleted);

        /**
         * @return a recorder that counts nothing (no meter registry)
         */
        static Recorder none() {
            return (jobName, succeeded, deleted) -> { };
        }
    }
}
