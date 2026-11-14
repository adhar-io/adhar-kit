package com.adhar.kit.batch.partition;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobExecutionException;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.partition.Partitioner;
import org.springframework.batch.core.partition.StepExecutionSplitter;
import org.springframework.batch.core.partition.support.SimpleStepExecutionSplitter;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * The splitter of every partitioned step the kit builds ({@link LocalPartitioning},
 * {@link RemotePartitioning}; design 0025 §5.10): Spring Batch's own splitting, except that the
 * partitioner's output is recorded as the run's <b>plan</b> before a single partition exists, and
 * every later attempt of the identity is split from that plan.
 *
 * <p>Why: {@link SimpleStepExecutionSplitter} saves each partition in two statements, the step
 * execution with an EMPTY context and then the partitioner's range, and on a restart it copies each
 * partition's last recorded context. A manager hard-killed between the two statements (on the first
 * split, or while a restart copies the contexts) therefore leaves a partition whose recorded range is
 * empty, the restart copies that, the reader binds its range to {@code null}, the partition reads
 * nothing and the run COMPLETES with rows missing. A manager killed between two partitions leaves the
 * later ones unrecorded, and a restart gives them a fresh range of today's input (an empty one for a
 * {@code PartitionNameProvider}, which {@link com.adhar.kit.batch.partitioner.SqlKeyRangePartitioner}
 * is), overlapping or missing what the recorded ranges cover. Both reproduced on PostgreSQL 18.</p>
 *
 * <p>With the plan ({@value #PLAN} in the manager step's context, which Spring Batch carries into
 * every restart of the step):</p>
 *
 * <ul>
 *   <li>the partitioner runs once per identity; a restart never partitions again, so rows that
 *       arrive later are not this instance's input (the rule {@code LocalPartitionedRestartTest} pins);</li>
 *   <li>a partition the plan names but no attempt recorded starts from its planned range;</li>
 *   <li>a partition whose last record was cut short (its context empty) resumes from its latest
 *       record that WAS written, and from its planned range when there
 *       is none: a record is only written by the split itself or by the partition running, so the
 *       latest written one is the last commit;</li>
 *   <li>everything else is Spring Batch's rule: a COMPLETED partition is skipped (unless the worker
 *       step allows a start if complete), FAILED or STOPPED resumes from its last commit, one still
 *       STARTING, STARTED or STOPPING or UNKNOWN refuses the restart.</li>
 * </ul>
 *
 * <p>When the manager's own restart copy was cut short too, the plan is taken from the latest
 * manager record that holds it. An identity with partitions but no plan anywhere was started before
 * the plan existed, and is split by {@link SimpleStepExecutionSplitter} as it was then.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class PartitionPlanSplitter implements StepExecutionSplitter {

    /** The manager step's execution-context key of the plan: partition name to the partitioner's context. */
    public static final String PLAN = "adhar.partition.plan";

    /** {@link SimpleStepExecutionSplitter}'s own key, kept so a manager step still reads as one. */
    static final String GRID_SIZE = SimpleStepExecutionSplitter.class.getSimpleName() + ".GRID_SIZE";

    private static final String SEPARATOR = ":";

    private final JobRepository jobRepository;
    private final String stepName;
    private final Partitioner partitioner;
    private final boolean allowStartIfComplete;

    /**
     * @param jobRepository        the shared JobRepository
     * @param stepName             the worker step's name: partitions are {@code <stepName>:<partition>}
     * @param partitioner          splits the input, once per identity
     * @param allowStartIfComplete whether a COMPLETED partition runs again on a restart (the worker step's flag)
     */
    public PartitionPlanSplitter(JobRepository jobRepository, String stepName, Partitioner partitioner,
                                 boolean allowStartIfComplete) {
        this.jobRepository = jobRepository;
        this.stepName = stepName;
        this.partitioner = partitioner;
        this.allowStartIfComplete = allowStartIfComplete;
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    @Override
    public Set<StepExecution> split(StepExecution manager, int gridSize) throws JobExecutionException {
        JobInstance instance = manager.getJobExecution().getJobInstance();
        ExecutionContext context = manager.getExecutionContext();
        Map<String, ExecutionContext> plan = read(context.get(PLAN));
        List<JobExecution> history = null;
        if (plan == null) {
            history = jobRepository.getJobExecutions(instance);
            plan = recordedPlan(history, manager);
            if (plan == null && hasPartitions(history, manager)) {
                log.info("Step {} was partitioned before the kit recorded a plan: splitting it as Spring Batch "
                        + "did then", manager.getStepName());
                SimpleStepExecutionSplitter legacy = new SimpleStepExecutionSplitter(jobRepository, stepName,
                        partitioner);
                legacy.setAllowStartIfComplete(allowStartIfComplete);
                return legacy.split(manager, gridSize);
            }
            if (plan == null) {
                int splitSize = (int) context.getLong(GRID_SIZE, gridSize);
                plan = new TreeMap<>(partitioner.partition(splitSize));
                context.putLong(GRID_SIZE, splitSize);
            } else if (!context.containsKey(GRID_SIZE)) {
                context.putLong(GRID_SIZE, plan.size());
            }
            context.put(PLAN, write(plan));
            // Durable before any partition exists: a split cut short is completed from it.
            jobRepository.updateExecutionContext(manager);
        }

        Set<StepExecution> partitions = new HashSet<>();
        for (Map.Entry<String, ExecutionContext> planned : plan.entrySet()) {
            String name = stepName + SEPARATOR + planned.getKey();
            StepExecution last = jobRepository.getLastStepExecution(instance, name);
            ExecutionContext start;
            if (last == null) {
                start = planned.getValue();
            } else if (!shouldStart(manager, last)) {
                continue;
            } else if (isUnwritten(last.getExecutionContext())) {
                if (history == null) {
                    history = jobRepository.getJobExecutions(instance);
                }
                Optional<ExecutionContext> written = lastWritten(history, name);
                log.warn("Partition {} (step execution {}) has no recorded context: its manager was stopped while "
                        + "saving it. Resuming it from {}", name, last.getId(), written.isPresent()
                        ? "its last written record" : "its planned range (it never ran)");
                start = written.orElseGet(planned::getValue);
            } else {
                start = last.getExecutionContext();
            }
            StepExecution partition = jobRepository.createStepExecution(name, manager.getJobExecution());
            partition.setExecutionContext(new ExecutionContext(start));
            jobRepository.updateExecutionContext(partition);
            partitions.add(partition);
        }
        return partitions;
    }

    private Map<String, ExecutionContext> recordedPlan(List<JobExecution> history, StepExecution manager) {
        return history.stream().flatMap(execution -> execution.getStepExecutions().stream())
                .filter(step -> step.getStepName().equals(manager.getStepName()) && step.getId() != manager.getId())
                .sorted(Comparator.comparingLong(StepExecution::getId).reversed())
                .map(step -> read(step.getExecutionContext().get(PLAN))).filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private boolean hasPartitions(List<JobExecution> history, StepExecution manager) {
        return history.stream().flatMap(execution -> execution.getStepExecutions().stream())
                .anyMatch(step -> step.getStepName().startsWith(stepName + SEPARATOR) && step.getId() != manager.getId());
    }

    private static Optional<ExecutionContext> lastWritten(List<JobExecution> history, String name) {
        return history.stream().flatMap(execution -> execution.getStepExecutions().stream())
                .filter(step -> step.getStepName().equals(name) && !isUnwritten(step.getExecutionContext()))
                .max(Comparator.comparingLong(StepExecution::getId)).map(StepExecution::getExecutionContext);
    }

    /**
     * @param context a partition's recorded context
     * @return whether it is the empty context a split saves before the range ({@code JobOperator.recover}
     *         marks a step recovered in memory only, so a recovered record is still empty)
     */
    static boolean isUnwritten(ExecutionContext context) {
        return context.isEmpty();
    }

    private static HashMap<String, HashMap<String, Object>> write(Map<String, ExecutionContext> plan) {
        HashMap<String, HashMap<String, Object>> value = new HashMap<>();
        plan.forEach((name, context) -> value.put(name, new HashMap<>(context.toMap())));
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, ExecutionContext> read(Object value) {
        if (!(value instanceof Map<?, ?> recorded)) {
            return null;
        }
        Map<String, ExecutionContext> plan = new TreeMap<>();
        recorded.forEach((name, context) -> plan.put(String.valueOf(name),
                new ExecutionContext(new HashMap<>((Map<String, Object>) context))));
        return plan;
    }

    /** Spring Batch's rule ({@code SimpleStepExecutionSplitter.shouldStart}, private there). */
    private boolean shouldStart(StepExecution manager, StepExecution last) throws JobExecutionException {
        BatchStatus status = last.getStatus();
        if (status == BatchStatus.UNKNOWN) {
            throw new JobExecutionException("Cannot restart step from UNKNOWN status. The last execution ended with "
                    + "a failure that could not be rolled back, so it may be dangerous to proceed. Manual "
                    + "intervention is probably necessary.");
        }
        if (status == BatchStatus.COMPLETED) {
            return allowStartIfComplete || manager.getJobExecutionId() == last.getJobExecutionId();
        }
        if (status == BatchStatus.STOPPED || status == BatchStatus.FAILED) {
            return true;
        }
        if (status == BatchStatus.STARTED || status == BatchStatus.STARTING || status == BatchStatus.STOPPING) {
            throw new JobExecutionException("Cannot restart step from " + status + " status. The old execution may "
                    + "still be executing, so you may need to verify manually that this is the case.");
        }
        throw new JobExecutionException("Cannot restart step from " + status + " status. We believe the old "
                + "execution was abandoned and therefore has been marked as un-restartable.");
    }
}
