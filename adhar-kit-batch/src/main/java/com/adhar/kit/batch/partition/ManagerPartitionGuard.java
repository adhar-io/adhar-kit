package com.adhar.kit.batch.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The manager half of remote partitioning's safety (design 0025 §5.9, §5.10), a listener of the
 * manager step:
 *
 * <ul>
 *   <li><b>Before the partitions are sent</b> it clears the run's message group: whatever a failed
 *       or stopped attempt left unclaimed is gone before the live attempt asks for its own
 *       partitions (F20; the workers' {@link StaleRequestGuard} catches a request claimed before
 *       the clear).</li>
 *   <li><b>While the partitions run</b> its {@link WorkerLivenessWatch} fails a partition whose
 *       worker pod is gone, so the run restarts it instead of waiting out the timeout.</li>
 *   <li><b>When the manager step ends without completing</b> (most often: it stopped waiting at
 *       {@code adhar.batch.partition.timeout}) it marks every partition still running FAILED. That is
 *       what {@code JobOperator.recover} does for a dead manager, and it is done here for the same
 *       reason, the fence of INV-4 (F19): a worker that is slow rather than dead would otherwise
 *       commit into a partition a restart is about to run again. Marking it moves its step
 *       execution's version, so the slow worker's next chunk commit fails and its business writes
 *       roll back with it. A restart then re-runs the partition from its last commit.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class ManagerPartitionGuard implements StepExecutionListener {

    private static final int FENCE_ATTEMPTS = 3;

    private final JdbcPartitionTransport transport;
    private final JobRepository jobRepository;
    private final String workerStepName;
    private final WorkerLivenessWatch watch;
    private final AtomicReference<AutoCloseable> watching = new AtomicReference<>();

    /**
     * @param transport      the partition channel
     * @param jobRepository  the shared JobRepository
     * @param workerStepName the worker step: its partitions are named {@code <workerStepName>:<partition>}
     * @param watch          watches the workers while the partitions run
     */
    public ManagerPartitionGuard(JdbcPartitionTransport transport, JobRepository jobRepository, String workerStepName,
                                 WorkerLivenessWatch watch) {
        this.transport = transport;
        this.jobRepository = jobRepository;
        this.workerStepName = workerStepName;
        this.watch = watch;
    }

    @Override
    public void beforeStep(StepExecution stepExecution) {
        String group = JdbcPartitionTransport.group(stepExecution.getJobExecution());
        int left = transport.pending(group);
        transport.clear(group);
        if (left > 0) {
            log.info("Cleared {} partition request(s) an earlier attempt of this run left in {}", left, group);
        }
        stopWatching();
        watching.set(watch.start(stepExecution));
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        stopWatching();
        // The group is NOT cleared here: it is the identity's, not this attempt's, and a manager that
        // ends late (recovered behind its back) would delete the live attempt's requests. The next
        // attempt clears it before it sends, and workers discard what is left meanwhile.
        if (stepExecution.getStatus() != BatchStatus.COMPLETED) {
            fenceUnfinishedPartitions(stepExecution);
        }
        // null keeps the step's own exit status.
        return null;
    }

    private void stopWatching() {
        AutoCloseable running = watching.getAndSet(null);
        if (running != null) {
            try {
                running.close();
            } catch (Exception ex) {
                log.debug("Could not stop the worker watch: {}", ex.getMessage());
            }
        }
    }

    private void fenceUnfinishedPartitions(StepExecution manager) {
        for (int attempt = 1; attempt <= FENCE_ATTEMPTS; attempt++) {
            try {
                fence(manager);
                return;
            } catch (OptimisticLockingFailureException ex) {
                // A worker committed between the read and the update: read again and mark it then.
                log.debug("A partition moved while it was being fenced (attempt {}): {}", attempt, ex.getMessage());
            }
        }
        log.warn("Could not fence the unfinished partitions of step execution {}: they kept moving. A restart "
                + "recovers them", manager.getId());
    }

    private void fence(StepExecution manager) {
        JobExecution current = jobRepository.getJobExecution(manager.getJobExecutionId());
        if (current == null) {
            return;
        }
        String prefix = workerStepName + ":";
        for (StepExecution partition : current.getStepExecutions()) {
            if (partition.getStepName().startsWith(prefix) && partition.getStatus().isRunning()) {
                log.warn("Partition {} (step execution {}) is still {} after the manager step ended {}: marking it "
                                + "FAILED so its worker can commit nothing more, and a restart re-runs it",
                        partition.getStepName(), partition.getId(), partition.getStatus(), manager.getStatus());
                partition.setStatus(BatchStatus.FAILED);
                partition.setEndTime(LocalDateTime.now());
                partition.setExitStatus(ExitStatus.FAILED.addExitDescription("Fenced by the manager: the manager step "
                        + "ended " + manager.getStatus() + " while this partition was still running"));
                jobRepository.update(partition);
            }
        }
    }
}
