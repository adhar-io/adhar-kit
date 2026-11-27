package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.event.BatchRunEvent;
import com.adhar.kit.batch.support.DebugLogCapture;
import com.adhar.kit.batch.support.RunnerJobs;
import com.adhar.kit.batch.support.TestJobs;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameter;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.infrastructure.item.ExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import com.adhar.kit.batch.support.FailureInjector;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@link AdharBatchRunner} (design 0025 §5.5) on a real JobRepository, with the owner Job and pods
 * served by a fake API server: every branch of the decision, its exit code, its summary.
 */
class AdharBatchRunnerTest extends RunnerFixture {

    private static String scheduledJob(String timestamp) {
        return "{\"metadata\":{\"uid\":\"job-uid-7\",\"annotations\":{\"" + KubernetesOwnerLookup.SCHEDULED_TIMESTAMP
                + "\":\"" + timestamp + "\"}},\"status\":{}}";
    }

    @Autowired
    FailureInjector failures;

    private void scheduled(String timestamp) {
        env.putAll(Map.of("ADHAR_BATCH_JOB", RunnerJobs.REPORT_JOB, "ADHAR_BATCH_TRIGGER", "schedule",
                "ADHAR_BATCH_K8S_JOB", "reporter-29470650"));
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/reporter-29470650").willReturn(
                okJson(scheduledJob(timestamp))));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/reporter-abc12").willReturn(okJson(
                "{\"metadata\":{\"uid\":\"uid-self\"},\"status\":{\"phase\":\"Running\",\"containerStatuses\":"
                        + "[{\"name\":\"batch\",\"imageID\":\"r/reporter@sha256:abc\"}]}}")));
    }

    private void manual(String job, String params) {
        env.putAll(Map.of("ADHAR_BATCH_JOB", job, "ADHAR_BATCH_TRIGGER", "manual", "ADHAR_BATCH_PARAMS", params));
    }

    private JsonNode summaryJson() throws Exception {
        return new ObjectMapper().readTree(summary());
    }

    private List<JobExecution> executions(String job, JobParameters identity) {
        JobInstance instance = jobRepository.getJobInstance(job, identity);
        return instance == null ? List.of() : jobRepository.getJobExecutions(instance);
    }

    private static JobParameters reportIdentity(String date) {
        return new JobParametersBuilder().addLocalDate("businessDate", LocalDate.parse(date))
                .addString("region", "sg").toJobParameters();
    }

    @Test
    @DisplayName("a scheduled run completes with exit 0: the business date is the CronJob's, provenance rides along")
    void scheduledRun() throws Exception {
        scheduled("2026-11-03T17:30:00Z");

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        JobParameters parameters = outcome.execution().getJobParameters();
        assertThat(parameters.getLocalDate("businessDate")).isEqualTo(LocalDate.of(2026, 11, 3));
        assertThat(parameters.getIdentifyingParameters()).extracting(JobParameter::name)
                .containsExactlyInAnyOrder("businessDate", "region");
        assertThat(parameters.getString(AdharBatchRunner.TRIGGER)).isEqualTo("schedule");
        assertThat(parameters.getString(AdharBatchRunner.K8S_JOB)).isEqualTo("reporter-29470650");
        assertThat(parameters.getString(AdharBatchRunner.APP_VERSION)).isEqualTo("sha256:abc");
        assertThat(parameters.getLong(AdharBatchRunner.ATTEMPT)).isEqualTo(1L);
        assertThat(jdbc.queryForList("SELECT SCHEDULED_TIME FROM SLOT_RUNS", String.class)).containsExactly("2026-11-03");

        JsonNode summary = summaryJson();
        assertThat(summary.get("outcome").asText()).isEqualTo("completed");
        assertThat(summary.get("batchJob").asText()).isEqualTo("reporter");
        assertThat(summary.get("businessDate").asText()).isEqualTo("2026-11-03");
        assertThat(summary.get("executionId").asLong()).isEqualTo(outcome.execution().getId());
        assertThat(summary.get("image").asText()).isEqualTo("sha256:abc");
        assertThat(stdout.toString()).contains(summary());
        assertThat(lease.find(outcome.execution().getId())).as("released when the run ended").isEmpty();
        assertThat(events).extracting(event -> event.getClass().getSimpleName()).containsExactly("Started", "Completed");
    }

    @Test
    @DisplayName("a serialization conflict at the start (another identity created at once) is retried: exit 0, not 1")
    void serializationConflictIsRetried() throws Exception {
        scheduled("2026-11-04T17:30:00Z");
        JobOperator real = jobOperator;
        jobOperator = mock(JobOperator.class, AdditionalAnswers.delegatesTo(real));
        doThrow(new CannotAcquireLockException("could not serialize access")).doAnswer(AdditionalAnswers.delegatesTo(real))
                .when(jobOperator).start(any(Job.class), any(JobParameters.class));

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        verify(jobOperator, times(2)).start(any(Job.class), any(JobParameters.class));
        assertThat(executions(RunnerJobs.REPORT_JOB, reportIdentity("2026-11-04"))).hasSize(1);
    }

    @Test
    @DisplayName("A1.2: the same business date again is exit 0 skipped already-complete, and no second execution")
    void sameBusinessDateTwice() throws Exception {
        scheduled("2026-11-02T17:30:00Z");
        run();
        events.clear();

        RunOutcome second = run();

        assertThat(second.exitCode()).isZero();
        assertThat(second.kind()).isEqualTo(RunOutcome.Kind.SKIPPED);
        assertThat(second.reason()).isEqualTo(RunOutcome.ALREADY_COMPLETE);
        assertThat(executions(RunnerJobs.REPORT_JOB, reportIdentity("2026-11-02"))).hasSize(1);
        assertThat(summaryJson().get("reason").asText()).isEqualTo("already-complete");
        assertThat(events(BatchRunEvent.Skipped.class)).singleElement()
                .extracting(BatchRunEvent.Skipped::getReason).isEqualTo("already-complete");

        properties.getExit().setAlreadyComplete(BatchProperties.Exit.AlreadyComplete.FAIL);
        assertThat(run().exitCode()).as("adhar.batch.exit.already-complete=fail").isEqualTo(3);
    }

    @Test
    @DisplayName("A1.9: invalid parameters exit 2 before any execution, with the validation message in the summary")
    void invalidParameters() throws Exception {
        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-03\",\"region\":\"xx\"}");

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isEqualTo(2);
        assertThat(outcome.reason()).isEqualTo("parameters");
        assertThat(summaryJson().get("exitDescription").asText()).contains("region must be one of [sg, my], was 'xx'");
        assertThat(summaryJson().has("executionId")).isFalse();
        assertThat(outcome.execution()).isNull();
    }

    @Test
    @DisplayName("every invalid request is exit 2 with its own reason")
    void invalidRequests() {
        manual(RunnerJobs.REPORT_JOB, "{}");
        assertThat(run().reason()).as("manual run without a business date").isEqualTo("business-date");

        manual(RunnerJobs.REPORT_JOB, "[\"2026-11-03\"]");
        assertThat(run().reason()).isEqualTo("parameters");
        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":");
        assertThat(run().reason()).isEqualTo("parameters");
        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":{\"y\":2026}}");
        assertThat(run().reason()).isEqualTo("parameters");

        manual("noSuchJob", "{}");
        RunOutcome unknown = run();
        assertThat(unknown.reason()).isEqualTo("unknown-job");
        assertThat(unknown.exitCode()).isEqualTo(2);

        manual(RunnerJobs.INCREMENTED_JOB, "{}");
        assertThat(run().reason()).isEqualTo("incrementer");

        env.remove("ADHAR_BATCH_JOB");
        assertThat(run().reason()).isEqualTo("unknown-job");

        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-03\"}");
        properties.setRole(BatchRole.WORKER);
        RunOutcome worker = run();
        assertThat(worker.exitCode()).isEqualTo(2);
        assertThat(worker.reason()).isEqualTo(RunOutcome.UNSUPPORTED);
        assertThat(worker.message()).as("without remote partitioning, a worker names what to add")
                .contains("spring-batch-integration", "spring-integration-jdbc");
    }

    @Test
    @DisplayName("a reserved distribution or partition transport is refused before anything runs, exit 2 unsupported")
    void reservedValuesAreRefused() {
        // A date no other test uses: the single run at the end completes this identity.
        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-09-17\"}");
        properties.setDistribution(com.adhar.kit.batch.config.BatchProperties.Distribution.CHUNKED);
        RunOutcome chunked = run();
        assertThat(chunked.exitCode()).isEqualTo(2);
        assertThat(chunked.reason()).isEqualTo("unsupported-distribution");
        assertThat(chunked.message()).contains("adhar.batch.distribution=chunked", "remote chunking",
                "not in this kit (design 0025, deferred)");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SLOT_RUNS", Integer.class)).isZero();

        properties.setDistribution(com.adhar.kit.batch.config.BatchProperties.Distribution.PARTITIONED);
        properties.getPartition().setTransport(com.adhar.kit.batch.config.BatchProperties.Partition.Transport.KAFKA);
        properties.setRole(BatchRole.MANAGER);
        RunOutcome kafka = run();
        assertThat(kafka.exitCode()).isEqualTo(2);
        assertThat(kafka.reason()).isEqualTo(RunOutcome.UNSUPPORTED);
        assertThat(kafka.message()).contains("adhar.batch.partition.transport=kafka");

        // A single run ignores the transport: only partitioned roles use it.
        properties.setRole(BatchRole.SINGLE);
        assertThat(run().exitCode()).isZero();
    }

    @Test
    @DisplayName("a chunked run is refused on EVERY pod, exit 2 unsupported-distribution: manager, worker and indexed "
            + "pods alike, and none runs the job as a single run")
    void chunkedIsRefusedInEveryRole() throws Exception {
        properties.setDistribution(BatchProperties.Distribution.CHUNKED);
        record Pod(BatchRole role, String index) { }
        for (Pod pod : List.of(new Pod(BatchRole.SINGLE, null), new Pod(BatchRole.MANAGER, null),
                new Pod(BatchRole.WORKER, null), new Pod(BatchRole.INDEXED, "0"), new Pod(BatchRole.INDEXED, "3"))) {
            manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-09-18\"}");
            env.remove("JOB_COMPLETION_INDEX");
            if (pod.index() != null) {
                env.put("JOB_COMPLETION_INDEX", pod.index());
            }
            properties.setRole(pod.role());

            RunOutcome outcome = run();

            assertThat(outcome.kind()).as("%s", pod).isEqualTo(RunOutcome.Kind.INVALID);
            assertThat(outcome.exitCode()).as("%s", pod).isEqualTo(2);
            assertThat(outcome.reason()).as("%s", pod).isEqualTo("unsupported-distribution");
            assertThat(outcome.execution()).as("%s", pod).isNull();
            JsonNode summary = summaryJson();
            assertThat(summary.get("outcome").asText()).as("%s", pod).isEqualTo("invalid");
            assertThat(summary.get("reason").asText()).as("%s", pod).isEqualTo("unsupported-distribution");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM SLOT_RUNS", Integer.class)).as("no step ran").isZero();
        assertThat(jobRepository.getJobInstances(RunnerJobs.REPORT_JOB, 0, 10)).as("nothing was started").isEmpty();
    }

    @Test
    @DisplayName("OPENLINEAGE_* set: one INFO line names the variables, never their values, and the run is unaffected")
    void lineageVariablesAreAnnouncedAndIgnored() {
        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-09-19\"}");
        try (DebugLogCapture log = new DebugLogCapture(AdharBatchRunner.class)) {
            assertThat(run().exitCode()).isZero();
            assertThat(log.events()).as("nothing to say without the variables")
                    .noneMatch(event -> event.getFormattedMessage().contains("OpenLineage"));
        }

        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-09-20\"}");
        env.putAll(Map.of("OPENLINEAGE_URL", "http://openmetadata.example:8585", "OPENLINEAGE_ENDPOINT", " ",
                "OPENLINEAGE_API_KEY", "bot-token-value"));
        try (DebugLogCapture log = new DebugLogCapture(AdharBatchRunner.class)) {
            RunOutcome outcome = run();

            assertThat(outcome.exitCode()).as("lineage never fails a run").isZero();
            assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
            List<ILoggingEvent> lines = log.events().stream()
                    .filter(event -> event.getFormattedMessage().contains("OpenLineage")).toList();
            assertThat(lines).as("once per run").hasSize(1);
            assertThat(lines.get(0).getLevel()).isEqualTo(Level.INFO);
            assertThat(lines.get(0).getFormattedMessage())
                    .startsWith("OPENLINEAGE_URL, OPENLINEAGE_API_KEY set, but OpenLineage emission is not in this kit")
                    .contains("design 0025, deferred")
                    .doesNotContain("OPENLINEAGE_ENDPOINT", "OPENLINEAGE_NAMESPACE", "bot-token-value", "openmetadata.example");
            assertThat(log.events().indexOf(lines.get(0))).as("at the start, before anything else is logged").isZero();
        }
    }

    @Test
    @DisplayName("a scheduled run whose Job carries no scheduled time exits 2 business-date rather than invent one")
    void scheduledWithoutTimestamp() {
        scheduled("2026-11-01T17:30:00Z");
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/reporter-29470650").willReturn(
                okJson("{\"metadata\":{\"uid\":\"u\"}}")));

        assertThat(run().reason()).isEqualTo("business-date");
    }

    @Test
    @DisplayName("an API that cannot be asked for the schedule is a start-up failure (exit 1), which the Job retries")
    void apiDown() throws Exception {
        scheduled("2026-11-01T17:30:00Z");
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/reporter-29470650").willReturn(
                aResponse().withStatus(503)));

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isEqualTo(1);
        assertThat(summaryJson().get("outcome").asText()).isEqualTo("failed");
    }

    @Test
    @DisplayName("a failure is exit 5, a non-retryable one exit 3; the summary says which")
    void failures() throws Exception {
        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-10-01\"}");
        RunnerJobs.FAILURE.set("retryable");
        assertThat(run().exitCode()).isEqualTo(5);
        assertThat(summaryJson().get("status").asText()).isEqualTo("FAILED");
        assertThat(events(BatchRunEvent.Failed.class)).singleElement()
                .extracting(BatchRunEvent.Failed::getExitCode).isEqualTo(5);

        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-10-02\"}");
        RunnerJobs.FAILURE.set("fatal");
        assertThat(run().exitCode()).isEqualTo(3);
    }

    @Test
    @DisplayName("A1.1 in process: a failed run's next pod restarts the same instance from its last commit, no duplicates")
    void restartResumes() {
        TestJobs.seed(jdbc, 25);
        manual(TestJobs.IMPORT_JOB, "{}");
        failures.failOn(15, 1);

        assertThat(run().exitCode()).isEqualTo(5);
        RunOutcome second = run();

        assertThat(second.exitCode()).isZero();
        assertThat(executions(TestJobs.IMPORT_JOB, new JobParameters())).hasSize(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM TARGET_ITEMS", Integer.class)).isEqualTo(25);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT ID) FROM TARGET_ITEMS", Integer.class)).isEqualTo(25);
        failures.clear();
    }

    @Test
    @DisplayName("an abandoned identity exits 6: it needs a new identity")
    void abandoned() throws Exception {
        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-05\"}");
        RunnerJobs.FAILURE.set("retryable");
        JobExecution failed = run().execution();
        jobOperator.abandon(failed);
        RunnerJobs.FAILURE.set("");

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isEqualTo(6);
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.ABANDONED);
    }

    /** A STARTED execution of the report for {@code date}, as a pod that is (or was) running it leaves it. */
    private JobExecution started(String date, String ownerPod, String ownerUid) {
        JobParameters identity = reportIdentity(date);
        JobInstance instance = jobRepository.createJobInstance(RunnerJobs.REPORT_JOB, identity);
        JobExecution execution = jobRepository.createJobExecution(instance, identity, new ExecutionContext());
        execution.setStatus(BatchStatus.STARTED);
        execution.setStartTime(LocalDateTime.now());
        jobRepository.update(execution);
        if (ownerPod != null) {
            lease.acquire(execution.getId(), new ExecutionLease.Owner(ownerPod, ownerUid, "worker-9"));
        }
        manual(RunnerJobs.REPORT_JOB, "{\"businessDate\":\"" + date + "\"}");
        return execution;
    }

    private void pod(String name, String json) {
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/" + name).willReturn(okJson(json)));
    }

    @Test
    @DisplayName("A1.2: another pod running the identity with a fresh lease makes this run exit 0 skipped duplicate")
    void duplicate() throws Exception {
        JobExecution running = started("2026-12-01", "reporter-other", "uid-other");
        pod("reporter-other", "{\"metadata\":{\"uid\":\"uid-other\"},\"status\":{\"phase\":\"Running\"}}");

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.reason()).isEqualTo(RunOutcome.DUPLICATE);
        assertThat(jobRepository.getJobExecution(running.getId()).getStatus()).isEqualTo(BatchStatus.STARTED);
        assertThat(summaryJson().get("instanceId").asLong()).isEqualTo(running.getJobInstance().getInstanceId());
    }

    @Test
    @DisplayName("an owner pod that is gone (404) is recovered: the old execution FAILED, a new one COMPLETED")
    void ownerGone() {
        JobExecution running = started("2026-12-02", "reporter-other", "uid-other");
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/reporter-other").willReturn(aResponse().withStatus(404)));

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.execution().getId()).isNotEqualTo(running.getId());
        assertThat(jobRepository.getJobExecution(running.getId()).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(lease.find(running.getId())).isEmpty();
        assertThat(events(BatchRunEvent.Recovered.class)).singleElement().satisfies(event -> {
            assertThat(event.getExecutionId()).isEqualTo(running.getId());
            assertThat(event.getReason()).contains("is gone");
        });
    }

    @Test
    @DisplayName("STOPPED with a step still running (killed before its step stopped) is recovered, never a duplicate forever")
    void stoppedWithARunningStep() {
        // A pod told to stop records STOPPED at once (JobOperator.stop); killed before its step stops,
        // it leaves the step STARTED, and Spring Batch refuses every restart as "already running".
        JobExecution stopped = started("2026-12-09", "reporter-other", "uid-other");
        StepExecution step = jobRepository.createStepExecution("reportStep", stopped);
        step.setStatus(BatchStatus.STARTED);
        jobRepository.update(step);
        stopped.setStatus(BatchStatus.STOPPED);
        stopped.setEndTime(LocalDateTime.now());
        jobRepository.update(stopped);
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/reporter-other").willReturn(aResponse().withStatus(404)));

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(outcome.execution().getId()).isNotEqualTo(stopped.getId());
        assertThat(jobRepository.getStepExecution(step.getId()).getStatus()).isEqualTo(BatchStatus.FAILED);
        assertThat(events(BatchRunEvent.Recovered.class)).singleElement()
                .satisfies(event -> assertThat(event.getExecutionId()).isEqualTo(stopped.getId()));
    }

    @Test
    @DisplayName("FAILED with a step still running and no lease is recovered at once, never a duplicate exit 0")
    void failedWithARunningStepAndNoLease() {
        // A manager whose step ended FAILED released its lease, but could not fence a partition a
        // worker was running: exit 0 here would end the gang (CompleteJob) or the Indexed Job (its
        // success policy) as a success.
        JobExecution failed = started("2026-12-19", null, null);
        StepExecution partition = jobRepository.createStepExecution("reportStep", failed);
        partition.setStatus(BatchStatus.STARTED);
        jobRepository.update(partition);
        failed.setStatus(BatchStatus.FAILED);
        failed.setEndTime(LocalDateTime.now());
        jobRepository.update(failed);

        RunOutcome outcome = run();

        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(outcome.execution().getId()).isNotEqualTo(failed.getId());
        assertThat(jobRepository.getStepExecution(partition.getId()).getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("a stale lease is recovered even though its pod still answers")
    void staleLease() {
        JobExecution running = started("2026-12-03", "reporter-other", "uid-other");
        pod("reporter-other", "{\"metadata\":{\"uid\":\"uid-other\"},\"status\":{\"phase\":\"Running\"}}");
        jdbc.update("UPDATE ADHAR_BATCH_EXECUTION_LEASE SET HEARTBEAT_AT = DATEADD('MINUTE', -5, CURRENT_TIMESTAMP)");

        assertThat(run().exitCode()).isZero();
        assertThat(jobRepository.getJobExecution(running.getId()).getStatus()).isEqualTo(BatchStatus.FAILED);
    }

    @Test
    @DisplayName("a terminating owner is waited for (polling), then recovered once it is gone")
    void terminatingOwner() {
        started("2026-12-04", "reporter-other", "uid-other");
        pod("reporter-other", "{\"metadata\":{\"uid\":\"uid-other\",\"deletionTimestamp\":\"2026-11-06T00:00:00Z\"},"
                + "\"status\":{\"phase\":\"Running\"}}");
        onSleep = () -> API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/reporter-other")
                .willReturn(aResponse().withStatus(404)));

        RunOutcome outcome = run();

        assertThat(sleeps).containsExactly(AdharBatchRunner.WAIT_POLL);
        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
    }

    @Test
    @DisplayName("an owner still terminating after recovery.max-wait fails the run retryably (exit 5)")
    void ownerNeverLeaves() {
        started("2026-12-05", "reporter-other", "uid-other");
        pod("reporter-other", "{\"metadata\":{\"uid\":\"uid-other\",\"deletionTimestamp\":\"2026-11-06T00:00:00Z\"},"
                + "\"status\":{\"phase\":\"Running\"}}");
        properties.getRecovery().setMaxWait(java.time.Duration.ofMillis(1));
        onSleep = () -> {
            try {
                Thread.sleep(5);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        };

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isEqualTo(5);
        assertThat(outcome.message()).contains("still owned by a terminating pod");
    }

    @Test
    @DisplayName("the lease is held and renewed while the execution runs")
    void leaseWhileRunning() throws Exception {
        properties.getRecovery().setHeartbeatInterval(java.time.Duration.ofMillis(50));
        RunnerJobs.CHUNK_MILLIS.set(40);
        manual(RunnerJobs.SLOW_CHUNK_JOB, "{\"run\":\"lease\"}");
        List<ExecutionLease.Lease> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
        relay.delegate = null;
        AdharBatchRunner runner = runner();
        var forward = relay.delegate;
        relay.delegate = new com.adhar.kit.batch.listener.BatchRunObserver() {
            @Override
            public void jobStarted(JobExecution execution) {
                forward.jobStarted(execution);
            }

            @Override
            public void chunkCommitted(org.springframework.batch.core.step.StepExecution execution, long items) {
                forward.chunkCommitted(execution, items);
                lease.find(execution.getJobExecutionId()).ifPresent(seen::add);
            }

            @Override
            public void jobFinished(JobExecution execution) {
                forward.jobFinished(execution);
            }
        };

        runner.run(null);

        assertThat(exitCodes.getExitCode()).isZero();
        assertThat(seen).isNotEmpty().allSatisfy(held -> {
            assertThat(held.owner()).isEqualTo(new ExecutionLease.Owner("reporter-abc12", "uid-self", "worker-1"));
            assertThat(held.age()).isLessThan(java.time.Duration.ofSeconds(2));
        });
        assertThat(seen.getLast().heartbeatAt()).as("renewed during the run").isAfter(seen.getFirst().acquiredAt());
        assertThat(seen.getLast().lastCommitAt()).as("the heartbeat carries the last commit").isNotNull();
    }
}
