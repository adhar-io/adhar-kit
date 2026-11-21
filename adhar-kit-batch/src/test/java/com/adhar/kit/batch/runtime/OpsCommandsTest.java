package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.ops.BatchCommandRunner;
import com.adhar.kit.batch.ops.DeadLetterReplayer;
import com.adhar.kit.batch.support.OpsJobs;
import com.adhar.kit.batch.support.RunnerJobs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The ops commands (design 0025 §5.2, §8.1, D4, D13, A3.5, A3.6) exactly as the CLI starts them
 * (cmd/batch/ops.go, dlq.go): the run template with the trigger {@code ops}, {@code ADHAR_BATCH_PARAMS={}},
 * {@code ADHAR_BATCH_COMMAND} and {@code ADHAR_BATCH_EXECUTION_ID}, on a real JobRepository. The
 * business job never runs; every command writes a summary with an outcome and a reason.
 */
class OpsCommandsTest extends RunnerFixture {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Spring Batch's tables, children first: these tests count instances, so each starts with none. */
    private static final List<String> SPRING_BATCH_TABLES = List.of("BATCH_STEP_EXECUTION_CONTEXT",
            "BATCH_STEP_EXECUTION", "BATCH_JOB_EXECUTION_CONTEXT", "BATCH_JOB_EXECUTION_PARAMS", "BATCH_JOB_EXECUTION",
            "BATCH_JOB_INSTANCE");

    @BeforeEach
    void emptyRepository() {
        SPRING_BATCH_TABLES.forEach(table -> jdbc.update("DELETE FROM " + table));
    }

    private void business(String job, String params) {
        env.putAll(Map.of("ADHAR_BATCH_JOB", job, "ADHAR_BATCH_TRIGGER", "manual", "ADHAR_BATCH_PARAMS", params));
        env.remove(AdharBatchRunner.COMMAND);
        env.remove(AdharBatchRunner.EXECUTION_ID);
    }

    private RunOutcome ops(String job, String command, Object executionId) {
        env.putAll(Map.of("ADHAR_BATCH_JOB", job, "ADHAR_BATCH_TRIGGER", "ops", "ADHAR_BATCH_PARAMS", "{}",
                AdharBatchRunner.COMMAND, command));
        if (executionId == null) {
            env.remove(AdharBatchRunner.EXECUTION_ID);
        } else {
            env.put(AdharBatchRunner.EXECUTION_ID, String.valueOf(executionId));
        }
        return run();
    }

    private JsonNode summaryJson() throws Exception {
        return JSON.readTree(summary());
    }

    private static JobParameters reportIdentity(String date) {
        return new JobParametersBuilder().addLocalDate("businessDate", LocalDate.parse(date))
                .addString("region", "sg").toJobParameters();
    }

    /** A FAILED report for {@code date}, through the runner. */
    private JobExecution failedReport(String date) {
        RunnerJobs.FAILURE.set("retryable");
        business(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"" + date + "\"}");
        RunOutcome outcome = run();
        RunnerJobs.FAILURE.set("");
        assertThat(outcome.execution().getStatus()).isEqualTo(BatchStatus.FAILED);
        return outcome.execution();
    }

    private JobExecution completedReport(String date) {
        business(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"" + date + "\"}");
        RunOutcome outcome = run();
        assertThat(outcome.execution().getStatus()).isEqualTo(BatchStatus.COMPLETED);
        return outcome.execution();
    }

    /** A STARTED report for {@code date} with a running step, as a pod that is (or was) running it leaves it. */
    private JobExecution startedReport(String date, String ownerPod) {
        JobParameters identity = reportIdentity(date);
        JobInstance instance = jobRepository.createJobInstance(RunnerJobs.REPORT_JOB, identity);
        JobExecution execution = jobRepository.createJobExecution(instance, identity, new ExecutionContext());
        execution.setStatus(BatchStatus.STARTED);
        execution.setStartTime(LocalDateTime.now());
        jobRepository.update(execution);
        StepExecution step = jobRepository.createStepExecution("reportStep", execution);
        step.setStatus(BatchStatus.STARTED);
        jobRepository.update(step);
        lease.acquire(execution.getId(), new ExecutionLease.Owner(ownerPod, "uid-" + ownerPod, "worker-9"));
        return execution;
    }

    private void pod(String name, String phase, boolean terminating) {
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/" + name).willReturn(okJson("{\"metadata\":{\"uid\":\"uid-"
                + name + "\"" + (terminating ? ",\"deletionTimestamp\":\"2026-11-06T00:00:00Z\"" : "")
                + "},\"status\":{\"phase\":\"" + phase + "\"}}")));
    }

    private void podGone(String name) {
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/" + name).willReturn(aResponse().withStatus(404)));
    }

    private BatchStatus status(JobExecution execution) {
        return jobRepository.getJobExecution(execution.getId()).getStatus();
    }

    // -------------------------------------------------------------------------------------------
    // the contract with the CLI
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("an ops Job never runs the business job: no parameters resolved, no execution started")
    void opsNeverRunsTheBusinessJob() throws Exception {
        JobExecution failed = failedReport("2026-11-01");
        jdbc.update("DELETE FROM SLOT_RUNS");
        long executions = jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION", Long.class);
        events.clear();

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "status", failed.getId());

        assertThat(outcome.exitCode()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION", Long.class)).isEqualTo(executions);
        assertThat(jdbc.queryForList("SELECT RUNNER FROM SLOT_RUNS", String.class)).isEmpty();
        assertThat(events).isEmpty();
        JsonNode summary = summaryJson();
        assertThat(summary.get("command").asText()).isEqualTo("status");
        assertThat(summary.get("trigger").asText()).isEqualTo("ops");
        assertThat(summary.get("outcome").asText()).isEqualTo("completed");
        assertThat(summary.get("reason").asText()).isEqualTo("failed");
        assertThat(summary.get("executionId").asLong()).isEqualTo(failed.getId());
        assertThat(summary.get("instanceId").asLong()).isEqualTo(failed.getJobInstance().getInstanceId());
        assertThat(summary.get("status").asText()).isEqualTo("FAILED");
        assertThat(summary.has("read")).as("the business run's counts are not an ops run's").isFalse();
        assertThat(summary.has("steps")).isFalse();
    }

    @Test
    @DisplayName("an ops command runs on a template the runner would refuse to run (chunked, Phase 4)")
    void opsRunsWhereTheBusinessJobCannot() {
        JobExecution failed = failedReport("2026-11-02");
        properties.setDistribution(BatchProperties.Distribution.CHUNKED);

        assertThat(ops(RunnerJobs.REPORT_JOB, "status", failed.getId()).exitCode()).isZero();
    }

    @Test
    @DisplayName("a worker of a partitioned template leaves the command to its manager: exit 0, skipped worker")
    void workerSkips() throws Exception {
        JobExecution failed = failedReport("2026-11-03");
        properties.setRole(BatchRole.WORKER);

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "abandon", failed.getId());

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.SKIPPED);
        assertThat(outcome.reason()).isEqualTo(AdharBatchRunner.WORKER);
        assertThat(status(failed)).isEqualTo(BatchStatus.FAILED);
        assertThat(summaryJson().get("command").asText()).isEqualTo("abandon");
    }

    @Test
    @DisplayName("refusals exit 2 with their reason: unknown command, missing or bad id, unknown execution, other job")
    void refusals() throws Exception {
        JobExecution report = failedReport("2026-11-04");

        RunOutcome unknown = ops(RunnerJobs.REPORT_JOB, "explode", report.getId());
        assertThat(unknown.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(unknown.reason()).isEqualTo(BatchCommandRunner.UNKNOWN_COMMAND);
        assertThat(summaryJson().get("command").asText()).isEqualTo("explode");

        assertThat(ops(RunnerJobs.REPORT_JOB, "abandon", null).reason()).isEqualTo(BatchCommandRunner.EXECUTION_ID);
        assertThat(ops(RunnerJobs.REPORT_JOB, "recover", "x12").reason()).isEqualTo(BatchCommandRunner.EXECUTION_ID);
        assertThat(ops(RunnerJobs.REPORT_JOB, "stop", 0).reason()).isEqualTo(BatchCommandRunner.EXECUTION_ID);
        assertThat(ops(RunnerJobs.REPORT_JOB, "dlq-replay", null).reason()).isEqualTo(BatchCommandRunner.EXECUTION_ID);

        RunOutcome missing = ops(RunnerJobs.REPORT_JOB, "abandon", 987654);
        assertThat(missing.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(missing.reason()).isEqualTo(BatchCommandRunner.UNKNOWN_EXECUTION);
        JsonNode unknownSummary = summaryJson();
        assertThat(unknownSummary.has("executionId")).as("D13: absent when no execution exists").isFalse();
        assertThat(unknownSummary.has("status")).isFalse();
        assertThat(unknownSummary.get("exitDescription").asText()).contains("987654");

        RunOutcome other = ops(RunnerJobs.SLOW_CHUNK_JOB, "abandon", report.getId());
        assertThat(other.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(other.reason()).isEqualTo(BatchCommandRunner.OTHER_JOB);
        assertThat(status(report)).isEqualTo(BatchStatus.FAILED);
    }

    // -------------------------------------------------------------------------------------------
    // abandon
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("abandon marks a FAILED execution ABANDONED; its identity then exits 6; abandoning again is a no-op")
    void abandon() throws Exception {
        JobExecution failed = failedReport("2026-11-05");

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "abandon", failed.getId());

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(outcome.reason()).isEqualTo("abandoned");
        assertThat(status(failed)).isEqualTo(BatchStatus.ABANDONED);
        JsonNode summary = summaryJson();
        assertThat(summary.get("status").asText()).isEqualTo("ABANDONED");
        assertThat(summary.get("reason").asText()).isEqualTo("abandoned");

        assertThat(ops(RunnerJobs.REPORT_JOB, "abandon", failed.getId()).reason()).isEqualTo("already-abandoned");

        business(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-05\"}");
        assertThat(run().exitCode()).isEqualTo(ExitCodes.ABANDONED);
    }

    @Test
    @DisplayName("abandon refuses a COMPLETED execution (exit 2 completed) and leaves it")
    void abandonCompleted() {
        JobExecution completed = completedReport("2026-11-06");

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "abandon", completed.getId());

        assertThat(outcome.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(outcome.reason()).isEqualTo(BatchCommandRunner.COMPLETED);
        assertThat(status(completed)).isEqualTo(BatchStatus.COMPLETED);
    }

    @Test
    @DisplayName("abandon refuses an execution whose owner pod is alive with a fresh lease (exit 2 owner-alive)")
    void abandonOwned() {
        JobExecution running = startedReport("2026-11-07", "reporter-live");
        pod("reporter-live", "Running", false);

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "abandon", running.getId());

        assertThat(outcome.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(outcome.reason()).isEqualTo(BatchCommandRunner.OWNER_ALIVE);
        assertThat(status(running)).isEqualTo(BatchStatus.STARTED);
        assertThat(lease.find(running.getId())).isPresent();
    }

    @Test
    @DisplayName("abandon of an execution left STARTED by a gone pod recovers its steps first, then abandons it")
    void abandonOrphan() {
        JobExecution running = startedReport("2026-11-08", "reporter-dead");
        podGone("reporter-dead");

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "abandon", running.getId());

        assertThat(outcome.reason()).isEqualTo("abandoned");
        JobExecution after = jobRepository.getJobExecution(running.getId());
        assertThat(after.getStatus()).isEqualTo(BatchStatus.ABANDONED);
        assertThat(after.getStepExecutions()).allSatisfy(step -> assertThat(step.getStatus()).isEqualTo(BatchStatus.FAILED));
        assertThat(lease.find(running.getId())).isEmpty();
    }

    // -------------------------------------------------------------------------------------------
    // recover
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("recover marks an execution left STARTED by a gone owner FAILED; the identity then restarts and completes")
    void recover() throws Exception {
        JobExecution running = startedReport("2026-11-09", "reporter-dead");
        podGone("reporter-dead");

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "recover", running.getId());

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.reason()).isEqualTo("recovered");
        assertThat(status(running)).isEqualTo(BatchStatus.FAILED);
        assertThat(lease.find(running.getId())).isEmpty();
        assertThat(summaryJson().get("status").asText()).isEqualTo("FAILED");

        business(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-09\"}");
        RunOutcome rerun = run();
        assertThat(rerun.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(rerun.execution().getJobInstance().getInstanceId())
                .isEqualTo(running.getJobInstance().getInstanceId());
    }

    @Test
    @DisplayName("recover refuses a live owner, needs nothing on an ended execution, refuses a completed one")
    void recoverRefusals() {
        JobExecution live = startedReport("2026-11-10", "reporter-live");
        pod("reporter-live", "Running", false);
        assertThat(ops(RunnerJobs.REPORT_JOB, "recover", live.getId()).reason()).isEqualTo(BatchCommandRunner.OWNER_ALIVE);
        assertThat(status(live)).isEqualTo(BatchStatus.STARTED);

        JobExecution failed = failedReport("2026-11-11");
        RunOutcome nothing = ops(RunnerJobs.REPORT_JOB, "recover", failed.getId());
        assertThat(nothing.exitCode()).isZero();
        assertThat(nothing.reason()).isEqualTo("nothing-to-recover");

        JobExecution completed = completedReport("2026-11-12");
        assertThat(ops(RunnerJobs.REPORT_JOB, "recover", completed.getId()).reason())
                .isEqualTo(BatchCommandRunner.COMPLETED);
    }

    @Test
    @DisplayName("recover waits for a terminating owner and recovers once it is gone")
    void recoverWaitsForATerminatingOwner() {
        JobExecution running = startedReport("2026-11-13", "reporter-ending");
        pod("reporter-ending", "Running", true);
        onSleep = () -> podGone("reporter-ending");

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "recover", running.getId());

        assertThat(sleeps).isNotEmpty();
        assertThat(outcome.reason()).isEqualTo("recovered");
        assertThat(status(running)).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("an owner still terminating after recovery.max-wait fails the command retryably (exit 5) and changes nothing")
    void recoverGivesUpOnAnOwnerThatNeverLeaves() {
        JobExecution running = startedReport("2026-11-14", "reporter-ending");
        pod("reporter-ending", "Running", true);
        MutableClock time = new MutableClock();
        clock = time;
        onSleep = () -> time.advance(Duration.ofMinutes(4));

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "recover", running.getId());

        assertThat(outcome.exitCode()).isEqualTo(ExitCodes.FAILED_RETRYABLE);
        assertThat(outcome.reason()).isEqualTo(BatchCommandRunner.OWNER_ALIVE);
        assertThat(status(running)).isEqualTo(BatchStatus.STARTED);
    }

    // -------------------------------------------------------------------------------------------
    // stop, status
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("stop marks a running execution STOPPING and reports stopped once its owner records STOPPED")
    void stop() throws Exception {
        JobExecution running = startedReport("2026-11-15", "reporter-live");
        // The owner sees the stop at its next chunk boundary, ends its job and lets the execution go.
        onSleep = () -> lease.release(running.getId());

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "stop", running.getId());

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.reason()).isEqualTo("stopped");
        assertThat(summaryJson().get("status").asText()).isEqualTo("STOPPED");
    }

    @Test
    @DisplayName("stop gives the owner adhar.batch.shutdown.timeout to stop its steps, then reports stop-requested")
    void stopRequested() {
        JobExecution running = startedReport("2026-11-16", "reporter-live");
        MutableClock time = new MutableClock();
        clock = time;
        onSleep = () -> time.advance(Duration.ofSeconds(30));

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "stop", running.getId());

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.reason()).isEqualTo("stop-requested");
        JobExecution after = jobRepository.getJobExecution(running.getId());
        assertThat(after.getStatus()).as("Spring Batch records a stop at once").isEqualTo(BatchStatus.STOPPED);
        assertThat(lease.find(running.getId())).as("the owner has not let it go").isPresent();
        assertThat(sleeps).hasSize((int) (properties.getShutdown().getTimeout().toSeconds() / 30));
    }

    /**
     * An ops stop whose owner is still on its way to the next chunk boundary: Spring Batch 6 has
     * already recorded the execution and its steps STOPPED, so only the lease says it is owned.
     */
    private JobExecution stopRequested(String date, String ownerPod) {
        JobExecution running = startedReport(date, ownerPod);
        pod(ownerPod, "Running", false);
        MutableClock time = new MutableClock();
        clock = time;
        onSleep = () -> time.advance(Duration.ofSeconds(30));
        assertThat(ops(RunnerJobs.REPORT_JOB, "stop", running.getId()).reason()).isEqualTo("stop-requested");
        onSleep = () -> { };
        clock = Clock.systemUTC();
        JobExecution after = jobRepository.getJobExecution(running.getId());
        assertThat(after.getStatus()).isEqualTo(BatchStatus.STOPPED);
        assertThat(after.getStepExecutions()).noneSatisfy(step -> assertThat(step.getStatus().isRunning()).isTrue());
        assertThat(lease.find(running.getId())).isPresent();
        return after;
    }

    @Test
    @DisplayName("§5.9: abandon and recover of a stop-requested execution whose owner still holds the lease are refused (owner-alive)")
    void aStopRequestedExecutionIsStillOwned() {
        JobExecution stopped = stopRequested("2026-11-21", "reporter-stopping");

        RunOutcome abandon = ops(RunnerJobs.REPORT_JOB, "abandon", stopped.getId());
        assertThat(abandon.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(abandon.reason()).isEqualTo(BatchCommandRunner.OWNER_ALIVE);
        assertThat(status(stopped)).isEqualTo(BatchStatus.STOPPED);
        assertThat(ops(RunnerJobs.REPORT_JOB, "recover", stopped.getId()).reason())
                .isEqualTo(BatchCommandRunner.OWNER_ALIVE);
        assertThat(lease.find(stopped.getId())).isPresent();

        // The owner is gone without letting go: recover takes the lease over and leaves the execution
        // STOPPED, which restarts; abandon then needs nothing more.
        podGone("reporter-stopping");
        RunOutcome recover = ops(RunnerJobs.REPORT_JOB, "recover", stopped.getId());
        assertThat(recover.exitCode()).isZero();
        assertThat(recover.reason()).isEqualTo("recovered");
        assertThat(status(stopped)).isEqualTo(BatchStatus.STOPPED);
        assertThat(lease.find(stopped.getId())).isEmpty();
        assertThat(ops(RunnerJobs.REPORT_JOB, "recover", stopped.getId()).reason()).isEqualTo("nothing-to-recover");
    }

    @Test
    @DisplayName("§5.9: abandon of a stop-requested execution whose owner is gone takes the lease and abandons it")
    void abandonAStopRequestedExecutionOfAGoneOwner() {
        JobExecution stopped = stopRequested("2026-11-22", "reporter-stopping");
        podGone("reporter-stopping");

        RunOutcome abandon = ops(RunnerJobs.REPORT_JOB, "abandon", stopped.getId());

        assertThat(abandon.reason()).isEqualTo("abandoned");
        assertThat(status(stopped)).isEqualTo(BatchStatus.ABANDONED);
        assertThat(lease.find(stopped.getId())).isEmpty();
    }

    @Test
    @DisplayName("§5.9: a run of the identity of a stop-requested execution whose owner is alive is a duplicate, not a second execution")
    void noRestartUnderAStopRequestedOwner() {
        JobExecution stopped = stopRequested("2026-11-23", "reporter-stopping");
        long executions = jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION", Long.class);

        business(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-23\"}");
        RunOutcome duplicate = run();

        assertThat(duplicate.exitCode()).isZero();
        assertThat(duplicate.kind()).isEqualTo(RunOutcome.Kind.SKIPPED);
        assertThat(duplicate.reason()).isEqualTo(RunOutcome.DUPLICATE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION", Long.class)).isEqualTo(executions);

        podGone("reporter-stopping");
        RunOutcome restarted = run();
        assertThat(restarted.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(restarted.execution().getJobInstance().getInstanceId()).isEqualTo(instance(stopped));
        assertThat(lease.find(stopped.getId())).as("the gone owner's lease is released").isEmpty();
        assertThat(events(com.adhar.kit.batch.event.BatchRunEvent.Recovered.class))
                .as("nothing in the repository was recovered: it had ended").isEmpty();
    }

    @Test
    @DisplayName("stop of an execution that is not running changes nothing: exit 0 not-running")
    void stopNotRunning() {
        JobExecution failed = failedReport("2026-11-17");

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "stop", failed.getId());

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.reason()).isEqualTo("not-running");
        assertThat(status(failed)).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("status without an execution id reports the job's latest execution, with its lease and running steps")
    void statusOfTheLatest() throws Exception {
        completedReport("2026-11-18");
        JobExecution running = startedReport("2026-11-19", "reporter-live");

        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "status", null);

        assertThat(outcome.reason()).isEqualTo("started");
        JsonNode summary = summaryJson();
        assertThat(summary.get("executionId").asLong()).isEqualTo(running.getId());
        assertThat(summary.get("runningSteps").asLong()).isEqualTo(1);
        assertThat(summary.has("leaseAgeSeconds")).isTrue();
        assertThat(ops(OpsJobs.LEDGER_JOB, "status", null).reason()).isEqualTo(BatchCommandRunner.UNKNOWN_EXECUTION);
    }

    // -------------------------------------------------------------------------------------------
    // housekeeping (D4, A3.5)
    // -------------------------------------------------------------------------------------------

    @Test
    @DisplayName("A3.5: after a COMPLETED run, housekeeping keeps the newest keepLast, the newest restartable FAILED and the running")
    void housekeepingAfterACompletedRun() {
        JobExecution d1 = completedReport("2026-10-01");
        JobExecution d2 = failedReport("2026-10-02");
        JobExecution d3 = completedReport("2026-10-03");
        JobExecution d4 = failedReport("2026-10-04");
        JobExecution d5 = completedReport("2026-10-05");
        JobExecution d6 = startedReport("2026-10-06", "reporter-live");
        d6.setStatus(BatchStatus.STOPPING);
        jobRepository.update(d6);
        JobExecution d7 = completedReport("2026-10-07");
        properties.getRetention().setDays(90);
        properties.getRetention().setKeepLast(2);
        clock = Clock.offset(Clock.systemUTC(), Duration.ofDays(200));

        RunOutcome d8 = completedReportOutcome("2026-10-08");

        assertThat(d8.exitCode()).isZero();
        List<Long> left = jdbc.queryForList("SELECT JOB_INSTANCE_ID FROM BATCH_JOB_INSTANCE WHERE JOB_NAME = ? "
                + "ORDER BY JOB_INSTANCE_ID", Long.class, RunnerJobs.REPORT_JOB);
        // d8 and d7 (the newest two), d6 (running), d4 (the newest FAILED); d1, d2, d3 and d5 are gone.
        assertThat(left).containsExactly(instance(d4), instance(d6), instance(d7),
                d8.execution().getJobInstance().getInstanceId());
        assertThat(jdbc.queryForList("SELECT JOB_EXECUTION_ID FROM BATCH_JOB_EXECUTION WHERE JOB_EXECUTION_ID IN (?, ?)",
                Long.class, d1.getId(), d2.getId())).as("an older FAILED is not the newest").isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_STEP_EXECUTION WHERE JOB_EXECUTION_ID IN (?, ?)",
                Long.class, d1.getId(), d2.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_EXECUTION_PARAMS WHERE JOB_EXECUTION_ID IN (?, ?)",
                Long.class, d3.getId(), d5.getId())).isZero();
        assertThat(lease.find(d6.getId())).as("a running execution keeps its lease").isPresent();
        assertThat(housekept).containsExactly(RunnerJobs.REPORT_JOB + ":succeeded:4");
        assertThat(summaryFields()).doesNotContain("command");
    }

    private RunOutcome completedReportOutcome(String date) {
        business(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"" + date + "\"}");
        return run();
    }

    private static long instance(JobExecution execution) {
        return execution.getJobInstance().getInstanceId();
    }

    private String summaryFields() {
        try {
            return String.join(",", (Iterable<String>) summaryJson()::fieldNames);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    @Test
    @DisplayName("nothing younger than retention.days is deleted, and no housekeeping runs without a retention")
    void housekeepingRespectsTheAge() {
        completedReport("2026-09-01");
        completedReport("2026-09-02");
        properties.getRetention().setKeepLast(0);
        completedReport("2026-09-03");
        assertThat(housekept).as("no retention.days, no housekeeping").isEmpty();

        properties.getRetention().setDays(90);
        completedReport("2026-09-04");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM BATCH_JOB_INSTANCE WHERE JOB_NAME = ?", Long.class,
                RunnerJobs.REPORT_JOB)).isEqualTo(4);
        assertThat(housekept).containsExactly(RunnerJobs.REPORT_JOB + ":succeeded:0");
    }

    @Test
    @DisplayName("D4: a housekeeping that fails is logged and counted, and the run still exits 0 completed")
    void housekeepingFailureNeverChangesTheExitCode() {
        completedReport("2026-08-01");
        properties.getRetention().setDays(1);
        properties.getRetention().setKeepLast(0);
        clock = Clock.offset(Clock.systemUTC(), Duration.ofDays(30));
        jdbc.execute("CREATE TRIGGER OPS_REFUSE_DELETE BEFORE DELETE ON BATCH_JOB_INSTANCE FOR EACH ROW CALL '"
                + RefuseDelete.class.getName() + "'");
        try {
            RunOutcome outcome = completedReportOutcome("2026-08-02");

            assertThat(outcome.exitCode()).isZero();
            assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
            assertThat(housekept).containsExactly(RunnerJobs.REPORT_JOB + ":failed:0");
        } finally {
            jdbc.execute("DROP TRIGGER OPS_REFUSE_DELETE");
        }
    }

    @Test
    @DisplayName("the ops verb housekeeping runs the same retention on demand and counts what it deleted")
    void housekeepingVerb() throws Exception {
        completedReport("2026-07-01");
        completedReport("2026-07-02");
        completedReport("2026-07-03");
        RunOutcome refused = ops(RunnerJobs.REPORT_JOB, "housekeeping", null);
        assertThat(refused.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(refused.reason()).isEqualTo(BatchCommandRunner.RETENTION);

        properties.getRetention().setDays(10);
        properties.getRetention().setKeepLast(1);
        clock = Clock.offset(Clock.systemUTC(), Duration.ofDays(11));
        RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "housekeeping", null);

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.reason()).isEqualTo("housekept");
        JsonNode summary = summaryJson();
        assertThat(summary.get("deleted").asLong()).isEqualTo(2);
        assertThat(summary.get("kept").asLong()).isEqualTo(1);
        assertThat(summaryFields()).contains("command,deleted,kept");
    }

    @Test
    @DisplayName("the ops verb housekeeping that fails exits 5 with reason housekeeping, and is counted")
    void housekeepingVerbFailure() {
        completedReport("2026-06-01");
        properties.getRetention().setDays(1);
        properties.getRetention().setKeepLast(0);
        clock = Clock.offset(Clock.systemUTC(), Duration.ofDays(30));
        jdbc.execute("CREATE TRIGGER OPS_REFUSE_DELETE BEFORE DELETE ON BATCH_JOB_INSTANCE FOR EACH ROW CALL '"
                + RefuseDelete.class.getName() + "'");
        try {
            RunOutcome outcome = ops(RunnerJobs.REPORT_JOB, "housekeeping", null);

            assertThat(outcome.exitCode()).isEqualTo(ExitCodes.FAILED_RETRYABLE);
            assertThat(outcome.reason()).isEqualTo("housekeeping");
            assertThat(housekept).containsExactly(RunnerJobs.REPORT_JOB + ":failed:0");
        } finally {
            jdbc.execute("DROP TRIGGER OPS_REFUSE_DELETE");
        }
    }

    /** An H2 trigger that refuses a delete, as a database that cannot be written to does. */
    public static class RefuseDelete implements org.h2.api.Trigger {
        @Override
        public void fire(java.sql.Connection connection, Object[] oldRow, Object[] newRow) throws java.sql.SQLException {
            throw new java.sql.SQLException("the database refuses the delete");
        }
    }

    // -------------------------------------------------------------------------------------------
    // dlq-replay (A3.6)
    // -------------------------------------------------------------------------------------------

    private RunOutcome ledger(String run) {
        business(OpsJobs.LEDGER_JOB, "{\"run\":\"" + run + "\"}");
        return run();
    }

    private List<Long> ledgerIds(String run) {
        return jdbc.queryForList("SELECT ID FROM OPS_LEDGER WHERE RUN = ? ORDER BY ID", Long.class, run);
    }

    private long pending(long executionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM ADHAR_BATCH_DEAD_LETTER WHERE JOB_EXECUTION_ID = ? "
                + "AND REPLAYED_AT IS NULL", Long.class, executionId);
    }

    @Test
    @DisplayName("A3.6: dlq-replay re-processes ONLY the execution's rejected items, under its own step scope")
    void replayOnlyTheRejected() throws Exception {
        OpsJobs.REJECT_PROCESS.addAll(List.of(2L, 5L));
        OpsJobs.REJECT_WRITE.add(8L);
        RunOutcome a = ledger("a");
        assertThat(a.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(ledgerIds("a")).containsExactly(1L, 3L, 4L, 6L, 7L, 9L);
        OpsJobs.REJECT_PROCESS.clear();
        OpsJobs.REJECT_PROCESS.add(4L);
        RunOutcome b = ledger("b");
        assertThat(pending(b.execution().getId())).isEqualTo(2);
        OpsJobs.reset();

        RunOutcome replay = ops(OpsJobs.LEDGER_JOB, "dlq-replay", a.execution().getId());

        assertThat(replay.exitCode()).isZero();
        assertThat(replay.reason()).isEqualTo("replayed");
        assertThat(ledgerIds("a")).as("with the original run's own parameter").containsExactly(1L, 2L, 3L, 4L, 5L,
                6L, 7L, 8L, 9L);
        assertThat(OpsJobs.PROCESSED).as("a write-phase reject is only written again").containsExactly(2L, 5L);
        assertThat(pending(a.execution().getId())).isZero();
        assertThat(pending(b.execution().getId())).as("another execution's are never touched").isEqualTo(2);
        assertThat(ledgerIds("b")).doesNotContain(4L, 8L);
        JsonNode summary = summaryJson();
        assertThat(summary.get("command").asText()).isEqualTo("dlq-replay");
        assertThat(summary.get("replayed").asLong()).isEqualTo(3);
        assertThat(summary.get("rejected").asLong()).isZero();
        assertThat(summary.get("executionId").asLong()).isEqualTo(a.execution().getId());
        assertThat(summaryFields()).contains("command,replayed,rejected");
        assertThat(status(a.execution())).as("the execution itself is not changed").isEqualTo(BatchStatus.COMPLETED);

        RunOutcome again = ops(OpsJobs.LEDGER_JOB, "dlq-replay", a.execution().getId());
        assertThat(again.reason()).isEqualTo("nothing-to-replay");
        assertThat(ledgerIds("a")).as("a replayed item is never written twice").hasSize(9);
    }

    @Test
    @DisplayName("an item that fails again stays rejected and the replay exits 3; the next replay after the fix gets it")
    void replayExitsNonZeroWhileAnyRemain() throws Exception {
        OpsJobs.REJECT_PROCESS.addAll(List.of(2L, 5L));
        RunOutcome a = ledger("a");
        OpsJobs.REJECT_PROCESS.remove(2L);

        RunOutcome partial = ops(OpsJobs.LEDGER_JOB, "dlq-replay", a.execution().getId());

        assertThat(partial.exitCode()).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        assertThat(partial.kind()).isEqualTo(RunOutcome.Kind.FAILED);
        assertThat(partial.reason()).isEqualTo(BatchCommandRunner.REJECTED);
        JsonNode summary = summaryJson();
        assertThat(summary.get("replayed").asLong()).isEqualTo(1);
        assertThat(summary.get("rejected").asLong()).isEqualTo(1);
        assertThat(ledgerIds("a")).contains(2L).doesNotContain(5L);

        OpsJobs.REJECT_PROCESS.clear();
        RunOutcome fixed = ops(OpsJobs.LEDGER_JOB, "dlq-replay", a.execution().getId());
        assertThat(fixed.exitCode()).isZero();
        assertThat(summaryJson().get("replayed").asLong()).isEqualTo(1);
        assertThat(ledgerIds("a")).filteredOn(id -> id == 2L).hasSize(1);
    }

    @Test
    @DisplayName("a dead letter with no item (a read skip) or of a step this application lacks stays rejected: exit 3")
    void unreplayableItemsStayRejected() {
        RunOutcome a = ledger("a");
        long id = a.execution().getId();
        jdbc.update("INSERT INTO ADHAR_BATCH_DEAD_LETTER (JOB_EXECUTION_ID, JOB_NAME, STEP_NAME, PHASE, ERROR_CLASS) "
                + "VALUES (?, ?, ?, 'read', 'java.io.IOException')", id, OpsJobs.LEDGER_JOB, OpsJobs.LEDGER_STEP);
        jdbc.update("INSERT INTO ADHAR_BATCH_DEAD_LETTER (JOB_EXECUTION_ID, JOB_NAME, STEP_NAME, PHASE, ITEM_TYPE, ITEM, "
                + "ERROR_CLASS) VALUES (?, ?, 'retiredStep', 'process', ?, '{\"id\":42,\"note\":\"x\"}', 'E')", id,
                OpsJobs.LEDGER_JOB, OpsJobs.Entry.class.getName());
        jdbc.update("INSERT INTO ADHAR_BATCH_DEAD_LETTER (JOB_EXECUTION_ID, JOB_NAME, STEP_NAME, PHASE, ITEM_TYPE, ITEM, "
                + "ERROR_CLASS) VALUES (?, ?, ?, 'process', 'com.example.Gone', '{}', 'E')", id, OpsJobs.LEDGER_JOB,
                OpsJobs.LEDGER_STEP);

        RunOutcome replay = ops(OpsJobs.LEDGER_JOB, "dlq-replay", id);

        assertThat(replay.exitCode()).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        assertThat(pending(id)).isEqualTo(3);
        assertThat(ledgerIds("a")).doesNotContain(42L);
    }

    @Test
    @DisplayName("dlq-replay of an execution housekeeping already deleted replays under a stand-in step scope")
    void replayAfterHousekeeping() {
        jdbc.update("INSERT INTO ADHAR_BATCH_DEAD_LETTER (JOB_EXECUTION_ID, JOB_NAME, STEP_NAME, BUSINESS_DATE, PHASE, "
                + "ITEM_TYPE, ITEM, ERROR_CLASS) VALUES (424242, ?, ?, DATE '2026-11-03', 'process', ?, "
                + "'{\"id\":77,\"note\":\"old\"}', 'E')", OpsJobs.LEDGER_JOB, OpsJobs.LEDGER_STEP,
                OpsJobs.Entry.class.getName());

        RunOutcome replay = ops(OpsJobs.LEDGER_JOB, "dlq-replay", 424242);

        assertThat(replay.exitCode()).isZero();
        assertThat(jdbc.queryForList("SELECT ID FROM OPS_LEDGER WHERE RUN IS NULL", Long.class)).containsExactly(77L);
        assertThat(ops(OpsJobs.LEDGER_JOB, "dlq-replay", 515151).reason())
                .isEqualTo(BatchCommandRunner.UNKNOWN_EXECUTION);
    }

    @Test
    @DisplayName("dlq-replay refuses another job's dead letters and a still running execution")
    void replayRefusals() {
        OpsJobs.REJECT_PROCESS.add(3L);
        RunOutcome a = ledger("a");
        OpsJobs.reset();

        assertThat(ops(RunnerJobs.REPORT_JOB, "dlq-replay", a.execution().getId()).reason())
                .isEqualTo(BatchCommandRunner.OTHER_JOB);
        jdbc.update("INSERT INTO ADHAR_BATCH_DEAD_LETTER (JOB_EXECUTION_ID, JOB_NAME, STEP_NAME, PHASE, ERROR_CLASS) "
                + "VALUES (636363, 'someoneElsesJob', 's', 'read', 'E')");
        assertThat(ops(OpsJobs.LEDGER_JOB, "dlq-replay", 636363).reason()).isEqualTo(BatchCommandRunner.OTHER_JOB);

        JobExecution running = startedReport("2026-11-20", "reporter-live");
        RunOutcome refused = ops(RunnerJobs.REPORT_JOB, "dlq-replay", running.getId());
        assertThat(refused.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(refused.reason()).isEqualTo(BatchCommandRunner.RUNNING);
        assertThat(pending(a.execution().getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("§5.9: dlq-replay of an ended execution whose owner still holds the lease is refused until the owner is gone")
    void replayWaitsForTheOwnerToLetGo() {
        OpsJobs.REJECT_PROCESS.add(3L);
        RunOutcome a = ledger("a");
        OpsJobs.reset();
        long id = a.execution().getId();
        // As an ops stop leaves it: STOPPED in the repository, the owner still writing to its next chunk.
        JobExecution stopped = jobRepository.getJobExecution(id);
        stopped.setStatus(BatchStatus.STOPPED);
        jobRepository.update(stopped);
        lease.acquire(id, new ExecutionLease.Owner("ledger-live", "uid-ledger-live", "worker-9"));
        pod("ledger-live", "Running", false);

        RunOutcome refused = ops(OpsJobs.LEDGER_JOB, "dlq-replay", id);

        assertThat(refused.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(refused.reason()).isEqualTo(BatchCommandRunner.OWNER_ALIVE);
        assertThat(pending(id)).isEqualTo(1);
        assertThat(ledgerIds("a")).doesNotContain(3L);

        podGone("ledger-live");
        RunOutcome replayed = ops(OpsJobs.LEDGER_JOB, "dlq-replay", id);
        assertThat(replayed.reason()).isEqualTo("replayed");
        assertThat(ledgerIds("a")).contains(3L);
        assertThat(lease.find(id)).isEmpty();
    }

    @Test
    @DisplayName("a row another replay claimed first is left to it: not written twice, not counted as replayed here")
    void aClaimedRowIsLeftToItsReplay() throws Exception {
        OpsJobs.REJECT_PROCESS.add(6L);
        RunOutcome a = ledger("a");
        OpsJobs.reset();
        long executionId = a.execution().getId();
        DeadLetterReplayer.Deserializer standard = DeadLetterReplayer.Deserializer.standard();
        // The other replay commits its claim between this one's read of the row and its own claim.
        DeadLetterReplayer racing = new DeadLetterReplayer(jdbc, "ADHAR_BATCH_DEAD_LETTER", replays, transactionManager,
                jobRepository, (type, json) -> {
                    jdbc.update("UPDATE ADHAR_BATCH_DEAD_LETTER SET REPLAYED_AT = CURRENT_TIMESTAMP WHERE "
                            + "JOB_EXECUTION_ID = ?", executionId);
                    return standard.read(type, json);
                }, Clock.systemUTC());

        DeadLetterReplayer.Result result = racing.replay(OpsJobs.LEDGER_JOB, executionId);

        assertThat(result.replayed()).isZero();
        assertThat(result.rejected()).isZero();
        assertThat(ledgerIds("a")).doesNotContain(6L);
        assertThat(racing.jobsOf(executionId)).containsExactly(OpsJobs.LEDGER_JOB);
    }

    /** A clock a test moves. */
    static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-11-21T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
