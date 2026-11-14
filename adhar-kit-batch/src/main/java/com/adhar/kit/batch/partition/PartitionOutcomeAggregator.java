package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.fault.NonRetryableBatchException;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.partition.StepExecutionAggregator;
import org.springframework.batch.core.partition.support.DefaultStepExecutionAggregator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.skip.SkipLimitExceededException;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the partitions a remote manager collects into the outcome the exit-code contract (design
 * 0025 §5.7) expects, which Spring Batch's own aggregation cannot see from the manager's process:
 *
 * <ul>
 *   <li><b>A partition stopped by its worker is a retryable failure, not a stop.</b> A worker pod
 *       that terminates stops only its own partition ({@link WorkerStopHandler}), and aggregating
 *       that partition would make the run {@code STOPPED}: exit 4, which §5.7 reserves for an
 *       operator's stop and which fails the Job without a retry. Unless somebody marked the job
 *       execution {@code STOPPING} (an operator, or this manager's own SIGTERM), the manager step
 *       fails instead (retryable: exit 5), and the restart re-runs the stopped partitions from their
 *       last commit.</li>
 *   <li><b>A partition that failed for a reason retrying cannot fix fails the run as such</b> (exit
 *       3): the worker's exception never reaches the manager's JVM, only its exit description does,
 *       so the classes named in it are checked for {@link NonRetryableBatchException} and
 *       {@link SkipLimitExceededException}. Best effort: the description is the stack trace cut to
 *       the repository's column, so a cause past the cut is not seen and the run stays retryable.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class PartitionOutcomeAggregator implements StepExecutionAggregator {

    /** A class name at the start of a stack-trace line: the exception itself or a cause. */
    private static final Pattern THROWABLE = Pattern.compile("(?m)^(?:Caused by: )?([\\p{L}_$][\\p{L}\\p{N}_$]*(?:\\.[\\p{L}_$][\\p{L}\\p{N}_$]*)+)(?::|$)");

    private final DefaultStepExecutionAggregator delegate = new DefaultStepExecutionAggregator();
    private final JobRepository jobRepository;

    /**
     * @param jobRepository the shared JobRepository, read for the job execution's current status
     */
    public PartitionOutcomeAggregator(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    @Override
    public void aggregate(StepExecution result, Collection<StepExecution> partitions) {
        delegate.aggregate(result, partitions);
        if (partitions == null) {
            return;
        }
        refresh(result.getJobExecution(), partitions);
        for (StepExecution partition : partitions) {
            if (partition.getStatus() == BatchStatus.FAILED) {
                Optional<String> cause = notRetryable(partition.getExitStatus().getExitDescription());
                if (cause.isPresent()) {
                    throw new NonRetryableBatchException("Partition " + partition.getStepName() + " failed with "
                            + cause.get() + ", which a restart cannot fix");
                }
            }
        }
        if (result.getStatus() != BatchStatus.STOPPED || stopWasRequested(result)) {
            return;
        }
        List<String> stopped = partitions.stream().filter(partition -> partition.getStatus() == BatchStatus.STOPPED)
                .map(StepExecution::getStepName).sorted().toList();
        // Thrown rather than set: the run's exit description, which the termination summary carries,
        // is the failure's message, not the manager step's exit status.
        throw new IllegalStateException("Partitions " + stopped + " were stopped by their workers' termination, not "
                + "by a stop of the run: a restart runs them from their last commit");
    }

    /**
     * The manager's job execution still holds the partitions as it created them, STARTING with no
     * counts: the workers ran them in other processes. The run's termination summary, events and
     * metrics read that job execution, so it is given what the repository recorded.
     */
    private static void refresh(JobExecution execution, Collection<StepExecution> finished) {
        for (StepExecution recorded : finished) {
            for (StepExecution local : execution.getStepExecutions()) {
                if (local != recorded && local.getId() == recorded.getId()) {
                    local.setStatus(recorded.getStatus());
                    local.setExitStatus(recorded.getExitStatus());
                    local.setStartTime(recorded.getStartTime());
                    local.setEndTime(recorded.getEndTime());
                    local.setLastUpdated(recorded.getLastUpdated());
                    local.setReadCount(recorded.getReadCount());
                    local.setWriteCount(recorded.getWriteCount());
                    local.setFilterCount(recorded.getFilterCount());
                    local.setCommitCount(recorded.getCommitCount());
                    local.setRollbackCount(recorded.getRollbackCount());
                    local.setReadSkipCount(recorded.getReadSkipCount());
                    local.setProcessSkipCount(recorded.getProcessSkipCount());
                    local.setWriteSkipCount(recorded.getWriteSkipCount());
                    local.setVersion(recorded.getVersion());
                }
            }
        }
    }

    private boolean stopWasRequested(StepExecution result) {
        JobExecution current = jobRepository.getJobExecution(result.getJobExecutionId());
        BatchStatus status = current != null ? current.getStatus() : result.getJobExecution().getStatus();
        return status == BatchStatus.STOPPING || status == BatchStatus.STOPPED;
    }

    /**
     * @param description a failed partition's exit description (its stack trace)
     * @return the not-retryable exception class it names, if any
     */
    static Optional<String> notRetryable(String description) {
        if (description == null || description.isEmpty()) {
            return Optional.empty();
        }
        Matcher matcher = THROWABLE.matcher(description);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (isNotRetryable(name)) {
                return Optional.of(name);
            }
        }
        return Optional.empty();
    }

    private static boolean isNotRetryable(String className) {
        if (className.equals(NonRetryableBatchException.class.getName())
                || className.equals(SkipLimitExceededException.class.getName())) {
            return true;
        }
        try {
            // The worker runs the same image: an application's own subclass loads here too.
            Class<?> type = Class.forName(className, false, PartitionOutcomeAggregator.class.getClassLoader());
            return NonRetryableBatchException.class.isAssignableFrom(type)
                    || SkipLimitExceededException.class.isAssignableFrom(type);
        } catch (ClassNotFoundException | LinkageError ex) {
            return false;
        }
    }
}
