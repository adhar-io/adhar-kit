package com.adhar.kit.batch.event;

import com.adhar.kit.batch.runtime.AdharBatchRunner;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import org.springframework.context.ApplicationEvent;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * What happened to a batch run, as an in-JVM Spring {@link ApplicationEvent} (design 0025 §5.14):
 * {@link Started}, {@link Completed}, {@link Failed}, {@link Stopped}, {@link Skipped} and
 * {@link Recovered}. Applications bridge them to business notifications; the platform's own
 * {@code adhar.batch} events come from the batch observer, never from a run pod.
 *
 * <pre>{@code
 * @EventListener
 * void on(BatchRunEvent event) {
 *     switch (event) {
 *         case BatchRunEvent.Failed failed -> pager.notify(failed.getJobName(), failed.getExitCode());
 *         case BatchRunEvent.Skipped skipped -> log.info("{} skipped: {}", skipped.getJobName(), skipped.getReason());
 *         default -> { }
 *     }
 * }
 * }</pre>
 *
 * <p>Each event carries its run's name ({@link #getRunName()}). Parameters are carried as text with
 * sensitive and credential-like values masked, never the {@code JobExecution} itself. {@link BatchJobFailedEvent} is still published alongside
 * {@link Failed}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public abstract sealed class BatchRunEvent extends ApplicationEvent {

    private final String jobName;
    private final Long instanceId;
    private final Long executionId;
    private final Map<String, String> parameters;

    BatchRunEvent(Object source, String jobName, Long instanceId, Long executionId, Map<String, String> parameters) {
        super(source);
        this.jobName = jobName;
        this.instanceId = instanceId;
        this.executionId = executionId;
        this.parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
    }

    /**
     * @return the job
     */
    public String getJobName() {
        return jobName;
    }

    /**
     * @return the JobInstance, {@code null} when the run has none (an invalid request)
     */
    public Long getInstanceId() {
        return instanceId;
    }

    /**
     * @return the execution, {@code null} when the run started none
     */
    public Long getExecutionId() {
        return executionId;
    }

    /**
     * @return the run's parameters as text, sensitive values masked
     */
    public Map<String, String> getParameters() {
        return parameters;
    }

    /**
     * @return the run's name: an in-service run's {@code adhar.run} (naming v1), else a Kubernetes
     *         run's Job ({@code adhar.k8s.job}); {@code null} when it has neither
     */
    public String getRunName() {
        String run = parameters.get(BatchScheduler.RUN_PARAMETER);
        return run != null ? run : parameters.get(AdharBatchRunner.K8S_JOB);
    }

    /** An execution started. */
    public static final class Started extends BatchRunEvent {

        /**
         * @param source      the publisher
         * @param jobName     the job
         * @param instanceId  the JobInstance
         * @param executionId the execution
         * @param parameters  the parameters, masked
         */
        public Started(Object source, String jobName, Long instanceId, Long executionId,
                       Map<String, String> parameters) {
            super(source, jobName, instanceId, executionId, parameters);
        }
    }

    /** An execution completed. */
    public static final class Completed extends BatchRunEvent {

        private final Duration duration;

        /**
         * @param source      the publisher
         * @param jobName     the job
         * @param instanceId  the JobInstance
         * @param executionId the execution
         * @param parameters  the parameters, masked
         * @param duration    how long it ran
         */
        public Completed(Object source, String jobName, Long instanceId, Long executionId,
                         Map<String, String> parameters, Duration duration) {
            super(source, jobName, instanceId, executionId, parameters);
            this.duration = duration;
        }

        /**
         * @return how long it ran
         */
        public Duration getDuration() {
            return duration;
        }
    }

    /** An execution failed. */
    public static final class Failed extends BatchRunEvent {

        private final int exitCode;
        private final List<String> failures;

        /**
         * @param source      the publisher
         * @param jobName     the job
         * @param instanceId  the JobInstance
         * @param executionId the execution
         * @param parameters  the parameters, masked
         * @param exitCode    3 (not retryable) or 5 (retryable)
         * @param failures    each failure's class and first message line
         */
        public Failed(Object source, String jobName, Long instanceId, Long executionId,
                      Map<String, String> parameters, int exitCode, List<String> failures) {
            super(source, jobName, instanceId, executionId, parameters);
            this.exitCode = exitCode;
            this.failures = failures == null ? List.of() : List.copyOf(failures);
        }

        /**
         * @return 3 (not retryable) or 5 (retryable)
         */
        public int getExitCode() {
            return exitCode;
        }

        /**
         * @return each failure's class and first message line
         */
        public List<String> getFailures() {
            return failures;
        }
    }

    /** An execution stopped (on request, or because the pod was terminating). */
    public static final class Stopped extends BatchRunEvent {

        /**
         * @param source      the publisher
         * @param jobName     the job
         * @param instanceId  the JobInstance
         * @param executionId the execution
         * @param parameters  the parameters, masked
         */
        public Stopped(Object source, String jobName, Long instanceId, Long executionId,
                       Map<String, String> parameters) {
            super(source, jobName, instanceId, executionId, parameters);
        }
    }

    /** A run did nothing: its identity already completed, another pod runs it, or no business day. */
    public static final class Skipped extends BatchRunEvent {

        private final String reason;

        /**
         * @param source      the publisher
         * @param jobName     the job
         * @param instanceId  the JobInstance, when known
         * @param executionId the execution found, when there is one
         * @param parameters  the parameters, masked
         * @param reason      {@code already-complete}, {@code duplicate} or {@code calendar}
         */
        public Skipped(Object source, String jobName, Long instanceId, Long executionId,
                       Map<String, String> parameters, String reason) {
            super(source, jobName, instanceId, executionId, parameters);
            this.reason = reason;
        }

        /**
         * @return {@code already-complete}, {@code duplicate} or {@code calendar}
         */
        public String getReason() {
            return reason;
        }
    }

    /** A run took over an execution whose owner was gone or silent, before restarting it. */
    public static final class Recovered extends BatchRunEvent {

        private final String reason;

        /**
         * @param source      the publisher
         * @param jobName     the job
         * @param instanceId  the JobInstance
         * @param executionId the execution recovered (now FAILED)
         * @param parameters  the parameters, masked
         * @param reason      why it was taken over
         */
        public Recovered(Object source, String jobName, Long instanceId, Long executionId,
                         Map<String, String> parameters, String reason) {
            super(source, jobName, instanceId, executionId, parameters);
            this.reason = reason;
        }

        /**
         * @return why it was taken over
         */
        public String getReason() {
            return reason;
        }
    }
}
