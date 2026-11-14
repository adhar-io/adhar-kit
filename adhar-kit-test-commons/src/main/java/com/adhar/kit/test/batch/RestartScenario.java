package com.adhar.kit.test.batch;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The restart test every batch job owes (design 0025 §5.19, A0.5): fail the job at item N, start it
 * again with the SAME parameters, and prove the restart resumed the same JobInstance from its last
 * commit and wrote every row exactly once.
 *
 * <pre>{@code
 * RestartScenario.of(jobs)                                  // the @AdharBatchTest's JobOperatorTestUtils
 *         .withParameters(params)
 *         .failingAt(failures, 150L)                        // the FailureInjector bean
 *         .run()
 *         .assertStepHistory("importStep", BatchStatus.FAILED, BatchStatus.COMPLETED)
 *         .assertResumedFromLastCommit("importStep", 1_000)
 *         .assertExactlyOnce(jdbc, "records", "record_key", 1_000);
 * }</pre>
 *
 * @since 0.1.0
 */
public final class RestartScenario {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)?");

    private final JobOperator operator;
    private final Job job;
    private JobParameters parameters = new JobParameters();
    private FailureInjector injector;
    private Object item;

    private RestartScenario(JobOperator operator, Job job) {
        this.operator = Objects.requireNonNull(operator, "operator");
        this.job = Objects.requireNonNull(job, "job");
    }

    /**
     * @param operator the context's JobOperator
     * @param job      the job under test
     * @return the scenario
     */
    public static RestartScenario of(JobOperator operator, Job job) {
        return new RestartScenario(operator, job);
    }

    /**
     * @param jobs {@code @SpringBatchTest}'s utilities, with their job set
     * @return the scenario
     */
    public static RestartScenario of(JobOperatorTestUtils jobs) {
        return new RestartScenario(jobs.getJobOperator(), jobs.getJob());
    }

    /**
     * @param parameters the identity both runs share (no incrementer: a restart needs the same instance)
     * @return this scenario
     */
    public RestartScenario withParameters(JobParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "parameters");
        return this;
    }

    /**
     * @param injector the application's {@link FailureInjector}
     * @param item     the key at which the first run fails, once
     * @return this scenario
     */
    public RestartScenario failingAt(FailureInjector injector, Object item) {
        this.injector = Objects.requireNonNull(injector, "injector");
        this.item = Objects.requireNonNull(item, "item");
        return this;
    }

    /**
     * Runs the job, which must FAIL at the planned item, then starts it again with the same
     * parameters, which must COMPLETE the same JobInstance.
     *
     * @return both executions, to assert on
     * @throws Exception      from the JobOperator
     * @throws AssertionError when the failure did not fail the run, or the restart did not complete it
     */
    public Outcome run() throws Exception {
        if (injector == null) {
            throw new IllegalStateException("failingAt(injector, item) is required");
        }
        int injectedBefore = injector.injected();
        injector.failOn(item, 1);
        JobExecution failed = operator.start(job, parameters);
        if (failed.getStatus() != BatchStatus.FAILED) {
            throw new AssertionError("The first run should FAIL at item " + item + " but ended " + failed.getStatus()
                    + (injector.injected() == injectedBefore ? ": the item was never checked (is FailureInjector.check called?)" : ""));
        }
        JobExecution restarted = operator.start(job, parameters);
        if (restarted.getStatus() != BatchStatus.COMPLETED) {
            throw new AssertionError("The restart should COMPLETE but ended " + restarted.getStatus() + " "
                    + restarted.getAllFailureExceptions());
        }
        if (!Objects.equals(failed.getJobInstance().getInstanceId(), restarted.getJobInstance().getInstanceId())) {
            throw new AssertionError("The restart started a NEW JobInstance (" + restarted.getJobInstance().getInstanceId()
                    + " instead of " + failed.getJobInstance().getInstanceId()
                    + "): does the job have an incrementer, or do the parameters differ?");
        }
        return new Outcome(failed, restarted);
    }

    /**
     * The two executions of a {@link RestartScenario}.
     *
     * @param failed    the run that failed at the planned item
     * @param restarted the restart that completed
     */
    public record Outcome(JobExecution failed, JobExecution restarted) {

        /**
         * @param stepName a step
         * @return its status in the failed run then in the restart (absent where it did not run)
         */
        public List<BatchStatus> stepHistory(String stepName) {
            List<BatchStatus> history = new ArrayList<>();
            step(failed, stepName).ifPresent(step -> history.add(step.getStatus()));
            step(restarted, stepName).ifPresent(step -> history.add(step.getStatus()));
            return history;
        }

        /**
         * @param stepName a step
         * @param expected its statuses across the two runs, e.g. FAILED, COMPLETED
         * @return this outcome
         */
        public Outcome assertStepHistory(String stepName, BatchStatus... expected) {
            List<BatchStatus> history = stepHistory(stepName);
            if (!history.equals(Arrays.asList(expected))) {
                throw new AssertionError("Step " + stepName + " should have run " + Arrays.toString(expected)
                        + " but ran " + history);
            }
            return this;
        }

        /**
         * For a step that neither filters nor skips: the restart read only what the failed run had
         * not committed.
         *
         * @param stepName   the step that failed
         * @param totalItems the items the step reads in a complete run
         * @return this outcome
         */
        public Outcome assertResumedFromLastCommit(String stepName, long totalItems) {
            StepExecution first = step(failed, stepName).orElseThrow(() ->
                    new AssertionError("Step " + stepName + " did not run in the failed run"));
            StepExecution second = step(restarted, stepName).orElseThrow(() ->
                    new AssertionError("Step " + stepName + " did not run in the restart"));
            if (first.getCommitCount() == 0) {
                throw new AssertionError("Step " + stepName + " failed before its first commit, so a resume cannot be "
                        + "told from a start over: fail at a later item");
            }
            long expected = totalItems - first.getWriteCount();
            if (second.getReadCount() != expected) {
                throw new AssertionError("The restart of " + stepName + " should read the " + expected
                        + " items after the last commit but read " + second.getReadCount()
                        + (second.getReadCount() == totalItems ? ": it started over (is the reader restartable?)" : ""));
            }
            return this;
        }

        /**
         * @param jdbc          the application's database
         * @param table         the table the job writes
         * @param keyColumn     its business key
         * @param expectedRows  how many rows a complete run writes
         * @return this outcome
         */
        public Outcome assertExactlyOnce(JdbcTemplate jdbc, String table, String keyColumn, long expectedRows) {
            if (!IDENTIFIER.matcher(table).matches() || !IDENTIFIER.matcher(keyColumn).matches()) {
                throw new IllegalArgumentException("table and key must be plain identifiers");
            }
            Long rows = jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
            Long distinct = jdbc.queryForObject("SELECT COUNT(DISTINCT " + keyColumn + ") FROM " + table, Long.class);
            if (!Objects.equals(rows, distinct)) {
                throw new AssertionError(table + " has " + rows + " rows for " + distinct + " keys: the restart wrote "
                        + (rows - distinct) + " rows twice");
            }
            if (rows == null || rows != expectedRows) {
                throw new AssertionError(table + " should have " + expectedRows + " rows but has " + rows);
            }
            return this;
        }

        private static java.util.Optional<StepExecution> step(JobExecution execution, String stepName) {
            return execution.getStepExecutions().stream().filter(step -> step.getStepName().equals(stepName)).findFirst();
        }
    }
}
