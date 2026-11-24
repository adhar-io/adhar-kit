package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.event.BatchRunEvent;
import com.adhar.kit.batch.support.RunnerJobs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.ExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The runner's Phase 3 guards (design 0025 §15, D5, D13): the business calendar, control totals
 * and the version-safe restart guard, each with its exit code and summary reason.
 */
class RunnerGuardsTest extends RunnerFixture {

    // The JobRepository outlives each test: every test uses business dates of its own.

    private Path calendarFile;

    @AfterEach
    void removeCalendar() throws Exception {
        if (calendarFile != null) {
            Files.deleteIfExists(calendarFile);
        }
    }

    private void calendar(String yaml) throws Exception {
        calendarFile = Files.createTempFile("calendar", ".yaml");
        Files.writeString(calendarFile, yaml);
        properties.setCalendar(calendarFile.toString());
    }

    private void scheduled(String timestamp) {
        env.putAll(Map.of("ADHAR_BATCH_JOB", RunnerJobs.REPORT_JOB, "ADHAR_BATCH_TRIGGER", "schedule",
                "ADHAR_BATCH_K8S_JOB", "reporter-29470650"));
        API.stubFor(get("/apis/batch/v1/namespaces/team-a-prod/jobs/reporter-29470650").willReturn(okJson(
                "{\"metadata\":{\"uid\":\"job-uid-7\",\"annotations\":{\"" + KubernetesOwnerLookup.SCHEDULED_TIMESTAMP
                        + "\":\"" + timestamp + "\"}},\"status\":{}}")));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/reporter-abc12").willReturn(okJson(
                "{\"metadata\":{\"uid\":\"uid-self\"},\"status\":{\"phase\":\"Running\",\"containerStatuses\":"
                        + "[{\"name\":\"batch\",\"imageID\":\"r/reporter@sha256:new\"}]}}")));
    }

    private void triggered(String trigger, String job, String params) {
        env.putAll(Map.of("ADHAR_BATCH_JOB", job, "ADHAR_BATCH_TRIGGER", trigger, "ADHAR_BATCH_PARAMS", params));
    }

    private JsonNode summaryJson() throws Exception {
        return new ObjectMapper().readTree(summary());
    }

    private int executionsOfReport(String date) {
        JobInstance instance = jobRepository.getJobInstance(RunnerJobs.REPORT_JOB, reportIdentity(date));
        return instance == null ? 0 : jobRepository.getJobExecutions(instance).size();
    }

    private static JobParameters reportIdentity(String date) {
        return new JobParametersBuilder().addLocalDate("businessDate", LocalDate.parse(date))
                .addString("region", "sg").toJobParameters();
    }

    private static final String SG_CALENDAR = """
            name: sg-business
            timeZone: Asia/Singapore
            holidays: [2026-11-05]
            businessDays: [2026-11-14]
            """;

    // ---------------------------------------------------------------------------------------
    // D5: the business calendar
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("D5: a scheduled run on a weekend day exits 0 skipped calendar, starts nothing, and says why")
    void weekendIsSkipped() throws Exception {
        calendar(SG_CALENDAR);
        scheduled("2026-11-07T01:00:00Z");

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isZero();
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.SKIPPED);
        assertThat(outcome.reason()).isEqualTo(RunOutcome.CALENDAR);
        assertThat(executionsOfReport("2026-11-07")).isZero();
        JsonNode summary = summaryJson();
        assertThat(summary.get("outcome").asText()).isEqualTo("skipped");
        assertThat(summary.get("reason").asText()).isEqualTo("calendar");
        assertThat(summary.get("businessDate").asText()).isEqualTo("2026-11-07");
        assertThat(summary.get("exitDescription").asText())
                .isEqualTo("2026-11-07 (SATURDAY) is not a business day of calendar [sg-business]");
        assertThat(summary.has("executionId")).isFalse();
        assertThat(events(BatchRunEvent.Skipped.class)).singleElement()
                .satisfies(event -> assertThat(event.getReason()).isEqualTo("calendar"));
    }

    @Test
    @DisplayName("D5: a holiday is skipped, a listed working Saturday and an ordinary weekday run")
    void holidaysAndBusinessDays() throws Exception {
        calendar(SG_CALENDAR);
        scheduled("2026-11-05T01:00:00Z");
        assertThat(run().reason()).isEqualTo(RunOutcome.CALENDAR);

        scheduled("2026-11-14T01:00:00Z");
        RunOutcome saturday = run();
        assertThat(saturday.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);

        scheduled("2026-11-18T01:00:00Z");
        assertThat(run().kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(jdbc.queryForList("SELECT SCHEDULED_TIME FROM SLOT_RUNS", String.class))
                .containsExactly("2026-11-14", "2026-11-18");
    }

    @Test
    @DisplayName("D5: backfill and dependency runs are judged by the business date they carry; a person's run never is")
    void whichTriggersTheCalendarAppliesTo() throws Exception {
        calendar(SG_CALENDAR);
        for (String trigger : List.of("backfill", "dependency")) {
            triggered(trigger, RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-08\"}");
            RunOutcome outcome = run();
            assertThat(outcome.reason()).as(trigger).isEqualTo(RunOutcome.CALENDAR);
            assertThat(outcome.exitCode()).isZero();
        }
        assertThat(executionsOfReport("2026-11-08")).isZero();

        for (String trigger : List.of("manual", "restart", "smoke")) {
            triggered(trigger, RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-08\"}");
            assertThat(run().kind()).as(trigger).isIn(RunOutcome.Kind.COMPLETED, RunOutcome.Kind.SKIPPED);
            assertThat(exitCodes.outcome().orElseThrow().reason()).as(trigger).isNotEqualTo(RunOutcome.CALENDAR);
        }
        assertThat(executionsOfReport("2026-11-08")).isEqualTo(1);
    }

    @Test
    @DisplayName("D5: a missing or invalid calendar file is exit 2 calendar-file; a person's run does not read it")
    void unusableCalendar() throws Exception {
        properties.setCalendar("/etc/adhar/batch/calendar/missing/calendar.yaml");
        scheduled("2026-11-04T01:00:00Z");
        RunOutcome missing = run();
        assertThat(missing.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(missing.reason()).isEqualTo(RunOutcome.CALENDAR_FILE);
        assertThat(missing.message()).contains("cannot be read");
        assertThat(summaryJson().get("outcome").asText()).isEqualTo("invalid");

        calendar("name: broken\nholiday: [2026-11-04]\n");
        RunOutcome invalid = run();
        assertThat(invalid.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(invalid.reason()).isEqualTo(RunOutcome.CALENDAR_FILE);
        assertThat(invalid.message()).contains("unknown key 'holiday'");
        assertThat(executionsOfReport("2026-11-04")).isZero();

        triggered("manual", RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-04\"}");
        assertThat(run().kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
    }

    @Test
    @DisplayName("D5: a calendar run with no business date at all is exit 2 business-date, never judged on today")
    void calendarWithoutABusinessDate() throws Exception {
        calendar(SG_CALENDAR);
        triggered("dependency", RunnerJobs.SLOW_CHUNK_JOB, "{\"run\":\"nightly\"}");

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(outcome.reason()).isEqualTo("business-date");
        assertThat(outcome.message()).contains("sg-business");
    }

    @Test
    @DisplayName("D5: a calendar with no zone judges the date the job's own zone makes of the scheduled time")
    void calendarWithoutAZone() throws Exception {
        calendar("name: weekdays\n");
        // Friday 17:00 UTC is Saturday 01:00 in Singapore.
        env.put("ADHAR_BATCH_TIME_ZONE", "Asia/Singapore");
        scheduled("2026-11-27T17:00:00Z");
        assertThat(run().reason()).isEqualTo(RunOutcome.CALENDAR);
        assertThat(summaryJson().get("businessDate").asText()).isEqualTo("2026-11-28");

        env.put("ADHAR_BATCH_TIME_ZONE", "UTC");
        scheduled("2026-11-27T17:00:00Z");
        assertThat(run().kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(executionsOfReport("2026-11-27")).isEqualTo(1);
        assertThat(executionsOfReport("2026-11-28")).isZero();
    }

    @Test
    @DisplayName("D5: a holiday skips the schedule and a backfill of it, never a person's run of the same date")
    void holidayForTheScheduleNotForAPerson() throws Exception {
        calendar(SG_CALENDAR);
        scheduled("2026-11-05T01:00:00Z");
        assertThat(run().reason()).isEqualTo(RunOutcome.CALENDAR);

        triggered("manual", RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-05\"}");
        assertThat(run().kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(executionsOfReport("2026-11-05")).isEqualTo(1);

        // The calendar is judged before the identity: the date stays a holiday, not an already-complete run.
        triggered("backfill", RunnerJobs.REPORT_JOB, "{\"businessDate\":\"2026-11-05\"}");
        RunOutcome backfill = run();
        assertThat(backfill.reason()).isEqualTo(RunOutcome.CALENDAR);
        assertThat(backfill.exitCode()).isZero();
    }

    @Test
    @DisplayName("D5: an empty calendar file or one naming no real zone is exit 2 calendar-file")
    void calendarFilesThatAreNotCalendars() throws Exception {
        scheduled("2026-11-25T01:00:00Z");
        calendar("");
        RunOutcome empty = run();
        assertThat(empty.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(empty.reason()).isEqualTo(RunOutcome.CALENDAR_FILE);

        calendar("name: mars\ntimeZone: Mars/Olympus\n");
        RunOutcome zone = run();
        assertThat(zone.reason()).isEqualTo(RunOutcome.CALENDAR_FILE);
        assertThat(zone.message()).contains("Mars/Olympus");
        assertThat(executionsOfReport("2026-11-25")).isZero();
    }

    @Test
    @DisplayName("D5: without a calendar every day is a business day")
    void noCalendar() {
        scheduled("2026-11-21T01:00:00Z");

        assertThat(run().kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
    }

    // ---------------------------------------------------------------------------------------
    // Control totals
    // ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("control totals that differ exit 3 failed control-totals; the corrected restart balances on the whole identity")
    void controlTotals() throws Exception {
        triggered("manual", RunnerJobs.TOTALS_JOB, "{\"run\":\"2026-11-04\",\"expected\":25}");

        RunOutcome mismatch = run();

        assertThat(mismatch.exitCode()).isEqualTo(ExitCodes.FAILED_NOT_RETRYABLE);
        assertThat(mismatch.kind()).isEqualTo(RunOutcome.Kind.FAILED);
        assertThat(mismatch.reason()).isEqualTo(RunOutcome.CONTROL_TOTALS);
        JsonNode summary = summaryJson();
        assertThat(summary.get("outcome").asText()).isEqualTo("failed");
        assertThat(summary.get("reason").asText()).isEqualTo("control-totals");
        assertThat(summary.get("read").asLong()).isEqualTo(20);
        assertThat(summary.get("exitDescription").asText()).contains("read 20 but 25 expected");
        assertThat(events(BatchRunEvent.Failed.class)).singleElement()
                .extracting(BatchRunEvent.Failed::getExitCode).isEqualTo(3);

        triggered("restart", RunnerJobs.TOTALS_JOB, "{\"run\":\"2026-11-04\",\"expected\":20}");
        RunOutcome corrected = run();

        assertThat(corrected.exitCode()).isZero();
        assertThat(corrected.execution().getJobInstance().getInstanceId())
                .isEqualTo(mismatch.execution().getJobInstance().getInstanceId());
        assertThat(corrected.execution().getStepExecutions()).singleElement()
                .satisfies(step -> assertThat(step.getReadCount()).as("nothing left to read").isZero());
        assertThat(summaryJson().has("reason")).isFalse();
    }

    // ---------------------------------------------------------------------------------------
    // The version-safe restart guard
    // ---------------------------------------------------------------------------------------

    /** A FAILED execution of the report for {@code date} that ran {@code steps} on image sha256:old. */
    private JobExecution failedOnTheOldImage(String date, BatchStatus status, String... steps) {
        JobParameters parameters = new JobParametersBuilder(reportIdentity(date))
                .addString(AdharBatchRunner.APP_VERSION, "sha256:old", false).toJobParameters();
        JobInstance instance = jobRepository.createJobInstance(RunnerJobs.REPORT_JOB, parameters);
        JobExecution execution = jobRepository.createJobExecution(instance, parameters, new ExecutionContext());
        execution.setStartTime(LocalDateTime.now());
        for (String name : steps) {
            StepExecution step = jobRepository.createStepExecution(name, execution);
            step.setStatus(name.equals(steps[steps.length - 1]) ? BatchStatus.FAILED : BatchStatus.COMPLETED);
            jobRepository.update(step);
        }
        execution.setStatus(status);
        if (!status.isRunning()) {
            execution.setEndTime(LocalDateTime.now());
        }
        jobRepository.update(execution);
        return execution;
    }

    @Test
    @DisplayName("a restart whose new image lost a step the identity ran is exit 2 definition-changed and starts nothing")
    void changedDefinitionIsRefused() throws Exception {
        JobExecution failed = failedOnTheOldImage("2026-11-10", BatchStatus.FAILED, "extractStep", "reportStep");
        scheduled("2026-11-10T01:00:00Z");

        RunOutcome outcome = run();

        assertThat(outcome.exitCode()).isEqualTo(ExitCodes.INVALID_REQUEST);
        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.INVALID);
        assertThat(outcome.reason()).isEqualTo(RunOutcome.DEFINITION_CHANGED);
        assertThat(outcome.message()).contains("[extractStep]").contains("execution " + failed.getId())
                .contains("sha256:old").contains("sha256:new").contains("adhar.batch.restart.definition-check=warn");
        assertThat(executionsOfReport("2026-11-10")).isEqualTo(1);
        JsonNode summary = summaryJson();
        assertThat(summary.get("reason").asText()).isEqualTo("definition-changed");
        assertThat(summary.get("instanceId").asLong()).isEqualTo(failed.getJobInstance().getInstanceId());
        assertThat(summary.has("executionId")).isFalse();
    }

    @Test
    @DisplayName("definition-check=warn restarts anyway, as Spring Batch would")
    void warnRestarts() {
        failedOnTheOldImage("2026-11-11", BatchStatus.FAILED, "extractStep", "reportStep");
        properties.getRestart().setDefinitionCheck(BatchProperties.Restart.DefinitionCheck.WARN);
        scheduled("2026-11-11T01:00:00Z");

        RunOutcome outcome = run();

        assertThat(outcome.kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
        assertThat(executionsOfReport("2026-11-11")).isEqualTo(2);
    }

    @Test
    @DisplayName("the same steps on a new image, an added step or partition executions are restarted")
    void compatibleRestarts() {
        failedOnTheOldImage("2026-11-12", BatchStatus.FAILED, "reportStep");
        scheduled("2026-11-12T01:00:00Z");
        assertThat(run().kind()).isEqualTo(RunOutcome.Kind.COMPLETED);

        failedOnTheOldImage("2026-11-13", BatchStatus.STOPPED, "reportStep:partition0", "reportStep:partition1");
        scheduled("2026-11-13T01:00:00Z");
        assertThat(run().kind()).isEqualTo(RunOutcome.Kind.COMPLETED);
    }

    @Test
    @DisplayName("a dead owner's execution of a changed job is refused before it is recovered: nothing is changed")
    void refusedBeforeRecovery() {
        JobExecution running = failedOnTheOldImage("2026-11-16", BatchStatus.STARTED, "extractStep", "reportStep");
        lease.acquire(running.getId(), new ExecutionLease.Owner("reporter-old", "uid-old", "worker-9"));
        API.stubFor(get("/api/v1/namespaces/team-a-prod/pods/reporter-old").willReturn(aResponse().withStatus(404)));
        scheduled("2026-11-16T01:00:00Z");

        RunOutcome outcome = run();

        assertThat(outcome.reason()).isEqualTo(RunOutcome.DEFINITION_CHANGED);
        assertThat(jobRepository.getJobExecution(running.getId()).getStatus()).isEqualTo(BatchStatus.STARTED);
        assertThat(lease.find(running.getId())).isPresent();
        assertThat(events(BatchRunEvent.Recovered.class)).isEmpty();
    }
}
