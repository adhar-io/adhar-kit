package com.adhar.kit.batch.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.job.flow.FlowHolder;
import org.springframework.batch.core.job.flow.FlowJob;
import org.springframework.batch.core.job.flow.FlowStep;
import org.springframework.batch.core.job.flow.State;
import org.springframework.batch.core.partition.PartitionStep;
import org.springframework.batch.core.partition.StepExecutionSplitter;
import org.springframework.batch.core.partition.support.TaskExecutorPartitionHandler;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.ListableStepLocator;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.step.StepHolder;
import org.springframework.beans.DirectFieldAccessor;
import org.springframework.beans.factory.InitializingBean;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The version-safe restart guard (design 0025 §15 Phase 3, F16): before a FAILED or STOPPED
 * execution is restarted, the job about to resume it must still have every step the identity's
 * executions ran.
 *
 * <p>Spring Batch restarts by step NAME: a step that completed is skipped, the one that failed
 * resumes from its saved ExecutionContext, and a name it has no record of starts from scratch. A
 * new image that renamed or removed a step therefore resumes into the wrong steps without a
 * word: the renamed step's work is done a second time under its new name, and a removed step that
 * had failed is simply never finished. Kargo promoting a new image while a run of the old one is
 * FAILED is exactly that (F16). Adding a step is safe, and so is any change that keeps the names.</p>
 *
 * <p>A partitioned step's worker is one of the job's steps: partition executions are named
 * {@code <worker>:<partition>}, and a restart finds each partition by that name, so a renamed worker
 * would start every partition again, the completed ones included. A partition execution is therefore
 * judged by its worker's name, the part before a {@code :} that names a step of the job. A job whose
 * steps cannot all be listed (a custom {@link Job}, a flow step whose flow cannot be read, or a
 * partitioned step whose splitter names no worker) is not checked, and that is logged.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public final class RestartGuard {

    /** Spring Batch's separator between a partitioned step's name and a partition's. */
    static final String PARTITION_SEPARATOR = ":";

    private final JobRepository jobRepository;

    /**
     * @param jobRepository where the identity's executions are read
     */
    public RestartGuard(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    /**
     * @param job          the job about to restart the instance
     * @param instance     the instance
     * @param imageDigest  the image this run is, empty when unknown
     * @return how the job changed incompatibly, empty when the restart is safe or cannot be judged
     */
    public Optional<Change> check(Job job, JobInstance instance, String imageDigest) {
        Optional<Set<String>> steps = stepNames(job);
        if (steps.isEmpty()) {
            log.info("Job [{}]: its steps cannot all be listed, so a restart is not checked against the steps its "
                    + "executions ran", job.getName());
            return Optional.empty();
        }
        List<JobExecution> executions = jobRepository.getJobExecutions(instance);
        Set<String> missing = new TreeSet<>();
        for (JobExecution execution : executions) {
            for (StepExecution step : execution.getStepExecutions()) {
                String name = step.getStepName();
                if (!isStepOf(name, steps.get())) {
                    missing.add(name);
                }
            }
        }
        if (missing.isEmpty()) {
            return Optional.empty();
        }
        JobExecution last = executions.stream().max(Comparator.comparingLong(JobExecution::getId)).orElseThrow();
        String ranOn = last.getJobParameters().getString(AdharBatchRunner.APP_VERSION);
        return Optional.of(new Change(job.getName(), last.getId(), missing, steps.get(),
                ranOn == null ? "" : ranOn, imageDigest == null ? "" : imageDigest));
    }

    /**
     * @param name  a step execution's name
     * @param steps the job's steps, workers included
     * @return whether it is one of them, or a partition ({@code <worker>:<partition>}) of one
     */
    static boolean isStepOf(String name, Set<String> steps) {
        if (steps.contains(name)) {
            return true;
        }
        // A worker's own name may hold the separator: every prefix ending before one is tried.
        for (int at = name.indexOf(PARTITION_SEPARATOR); at > 0; at = name.indexOf(PARTITION_SEPARATOR, at + 1)) {
            if (steps.contains(name.substring(0, at))) {
                return true;
            }
        }
        return false;
    }

    /**
     * @param job a job
     * @return every step name it can run, nested flows and partition workers included; empty when
     *         they cannot be listed
     */
    static Optional<Set<String>> stepNames(Job job) {
        if (!(job instanceof ListableStepLocator locator)) {
            return Optional.empty();
        }
        Set<String> names = new LinkedHashSet<>();
        try {
            // FlowJob lists the steps of its nested flows only when they were initialised before
            // its first listing, and caches what it found: its flow is walked here as well.
            if (job instanceof FlowJob && !(field(job, "flow") instanceof Flow flow && flow(flow, names))) {
                return Optional.empty();
            }
            for (String name : locator.getStepNames()) {
                names.add(name);
                if (!nested(locator.getStep(name), names)) {
                    return Optional.empty();
                }
            }
        } catch (RuntimeException ex) {
            log.debug("Job [{}]: listing its steps failed: {}", job.getName(), ex.toString());
            return Optional.empty();
        }
        return Optional.of(names);
    }

    /** Adds what a step runs inside it; false when that cannot be read. */
    private static boolean nested(Step step, Set<String> names) {
        if (step instanceof ListableStepLocator locator) {
            for (String name : locator.getStepNames()) {
                names.add(name);
                if (!nested(locator.getStep(name), names)) {
                    return false;
                }
            }
        }
        if (step instanceof FlowStep) {
            // FlowStep lists nothing and has no getter for its flow.
            return field(step, "flow") instanceof Flow nestedFlow && flow(nestedFlow, names);
        }
        if (step instanceof PartitionStep) {
            // Nor does PartitionStep for its splitter, whose step name is the partitions' prefix.
            if (!(field(step, "stepExecutionSplitter") instanceof StepExecutionSplitter splitter)
                    || splitter.getStepName() == null || splitter.getStepName().isBlank()) {
                return false;
            }
            names.add(splitter.getStepName());
            // A worker run in this process may itself be a flow: what it runs is listed too.
            return !(field(step, "partitionHandler") instanceof TaskExecutorPartitionHandler local)
                    || local.getStep() == null || nested(local.getStep(), names);
        }
        return true;
    }

    private static Object field(Object owner, String name) {
        return new DirectFieldAccessor(owner).getPropertyValue(name);
    }

    private static boolean flow(Flow flow, Set<String> names) {
        if (flow instanceof InitializingBean lazy && flow.getStates().isEmpty()) {
            // A SimpleFlow builds its states on first use; a flow built inside a job bean is no
            // bean itself, so nothing has asked yet. Building them is what its first start does.
            try {
                lazy.afterPropertiesSet();
            } catch (Exception ex) {
                throw new IllegalStateException("flow [" + flow.getName() + "] cannot be initialised", ex);
            }
        }
        for (State state : flow.getStates()) {
            if (state instanceof ListableStepLocator locator) {
                for (String name : locator.getStepNames()) {
                    names.add(name);
                    if (!nested(locator.getStep(name), names)) {
                        return false;
                    }
                }
            } else if (state instanceof StepHolder holder) {
                names.add(holder.getStep().getName());
                if (!nested(holder.getStep(), names)) {
                    return false;
                }
            } else if (state instanceof FlowHolder holder) {
                for (Flow subflow : holder.getFlows()) {
                    if (!flow(subflow, names)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * A job that no longer has steps the identity ran.
     *
     * @param job          the job
     * @param executionId  the identity's last execution
     * @param missing      the steps it ran that the job no longer has
     * @param steps        the steps the job has now
     * @param ranOnImage   the image of the last execution, empty when unknown
     * @param image        this run's image, empty when unknown
     */
    public record Change(String job, long executionId, Set<String> missing, Set<String> steps, String ranOnImage,
                         String image) {

        /**
         * @return whether the image is known to differ from the one that ran the last execution
         */
        public boolean imageChanged() {
            return !ranOnImage.isBlank() && !image.isBlank() && !ranOnImage.equals(image);
        }

        /**
         * @return what changed and what to do about it
         */
        public String message() {
            String images = imageChanged() ? " (it ran image " + ranOnImage + "; this is " + image + ")"
                    : ranOnImage.isBlank() ? "" : " (image " + ranOnImage + ")";
            return "Job [" + job + "] no longer has the step(s) " + missing + " that execution " + executionId
                    + " of this identity ran" + images + "; its steps are now " + steps + ". A restart would redo "
                    + "their work under another name or never finish it. Restart on the image that ran it, abandon "
                    + "execution " + executionId + " and run a new identity, or set "
                    + "adhar.batch.restart.definition-check=warn to restart anyway";
        }
    }
}
