package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.runtime.KubernetesOwnerLookup;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.KubernetesLookupException;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup.Pod;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The manager's watch over its workers (design 0025 §5.10, §4.5): while the manager step waits for
 * its partitions, it fails every partition still running whose {@link PartitionOwner} pod is gone
 * (404), replaced (another uid under its name, as a Volcano {@code RestartJob} makes) or ended.
 *
 * <p>Why: workers hold no lease and a request is consumed when it is claimed, so a worker SIGKILLed or
 * OOM-killed mid-partition leaves that partition STARTED for good, and nobody else can take it. On a
 * native Indexed Job nothing restarts the run for that (a worker index is simply replaced), and the
 * manager would wait until {@code adhar.batch.partition.timeout}, most of the run's deadline, before
 * failing it. Failed here, the other partitions still finish, the manager step then fails retryable
 * (exit 5) and its retry re-runs that partition from its last commit. On Volcano the gang's
 * {@code PodFailed → RestartJob} usually gets there first; this is harmless beside it.</p>
 *
 * <p>Marking the partition FAILED moves its step execution's version, so a worker that was only
 * unreachable, not dead, commits nothing more (INV-4). A pod that is terminating is left alone: its
 * worker is stopping the partition itself ({@link WorkerStopHandler}). Outside a cluster, or for a
 * partition no worker has claimed yet, there is nothing to ask and the manager's timeout stays the
 * only bound.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class WorkerLivenessWatch {

    private final JobRepository jobRepository;
    private final KubernetesOwnerLookup lookup;
    private final String workerStepName;
    private final Duration interval;

    /**
     * @param jobRepository  the shared JobRepository
     * @param lookup         reads the worker pods
     * @param workerStepName the worker step: its partitions are named {@code <workerStepName>:<partition>}
     * @param interval       how often the partitions are checked
     */
    public WorkerLivenessWatch(JobRepository jobRepository, KubernetesOwnerLookup lookup, String workerStepName,
                               Duration interval) {
        this.jobRepository = jobRepository;
        this.lookup = lookup;
        this.workerStepName = workerStepName;
        this.interval = interval;
    }

    /**
     * @param manager the manager step, now waiting for its partitions
     * @return the running watch, closed when the manager step ends; a no-op outside a cluster
     */
    public AutoCloseable start(StepExecution manager) {
        if (!lookup.isAvailable()) {
            return () -> { };
        }
        long jobExecutionId = manager.getJobExecutionId();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "batch-worker-watch");
            thread.setDaemon(true);
            return thread;
        });
        timer.scheduleWithFixedDelay(() -> {
            try {
                check(jobExecutionId);
            } catch (RuntimeException ex) {
                log.debug("Could not check the workers of execution {}: {}", jobExecutionId, ex.getMessage());
            }
        }, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
        return timer::shutdownNow;
    }

    /**
     * One pass over the execution's partitions.
     *
     * @param jobExecutionId the manager's job execution
     * @return how many partitions were failed
     */
    public int check(long jobExecutionId) {
        JobExecution execution = jobRepository.getJobExecution(jobExecutionId);
        if (execution == null) {
            return 0;
        }
        Map<String, Optional<String>> verdicts = new HashMap<>();
        int failed = 0;
        for (StepExecution partition : execution.getStepExecutions()) {
            if (!partition.getStepName().startsWith(workerStepName + ":") || !partition.getStatus().isRunning()) {
                continue;
            }
            Optional<PartitionOwner> owner = PartitionOwner.of(partition);
            if (owner.isEmpty()) {
                continue;
            }
            Optional<String> gone = verdicts.computeIfAbsent(owner.get().pod() + "/" + owner.get().uid(),
                    key -> gone(owner.get()));
            if (gone.isPresent() && fail(partition, owner.get(), gone.get())) {
                failed++;
            }
        }
        return failed;
    }

    private Optional<String> gone(PartitionOwner owner) {
        Optional<Pod> pod;
        try {
            pod = lookup.pod(owner.pod());
        } catch (KubernetesLookupException ex) {
            log.debug("Could not read worker pod {}: {}", owner.pod(), ex.getMessage());
            return Optional.empty();
        }
        if (pod.isEmpty()) {
            return Optional.of("is gone");
        }
        Pod current = pod.get();
        if (!owner.uid().isEmpty() && !current.uid().isEmpty() && !owner.uid().equals(current.uid())) {
            return Optional.of("was replaced");
        }
        if (current.isTerminal()) {
            return Optional.of("has ended (" + current.phase() + ")");
        }
        return Optional.empty();
    }

    private boolean fail(StepExecution partition, PartitionOwner owner, String gone) {
        log.warn("Partition {} (step execution {}) is still {} but its worker pod {} {}: marking it FAILED so the "
                        + "other partitions finish and a restart re-runs it from its last commit", partition.getStepName(),
                partition.getId(), partition.getStatus(), owner.pod(), gone);
        partition.setStatus(BatchStatus.FAILED);
        partition.setEndTime(LocalDateTime.now());
        partition.setExitStatus(ExitStatus.FAILED.addExitDescription("Failed by the manager: worker pod " + owner.pod()
                + " " + gone + " while this partition was running"));
        try {
            jobRepository.update(partition);
            return true;
        } catch (OptimisticLockingFailureException ex) {
            // It committed meanwhile: a worker is alive after all. The next pass looks again.
            log.debug("Partition {} moved while it was being failed: {}", partition.getStepName(), ex.getMessage());
            return false;
        }
    }
}
