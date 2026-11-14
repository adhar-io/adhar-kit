package com.adhar.kit.batch.event;

import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.RunnerJobs;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BatchRunEvent} and {@link BatchRunEventPublisher}: the six run events, published for
 * every execution the kit's listeners see, with sensitive values masked.
 */
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class, RunnerJobs.class,
        BatchRunEventTest.Capture.class})
class BatchRunEventTest {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.h2().forEach((key, value) -> registry.add(key, () -> value));
    }

    @Configuration(proxyBeanMethods = false)
    static class Capture {

        final List<BatchRunEvent> events = new CopyOnWriteArrayList<>();

        @EventListener
        void on(BatchRunEvent event) {
            events.add(event);
        }
    }

    @Autowired
    Capture capture;
    @Autowired
    JobOperator jobOperator;
    @Autowired
    @Qualifier(RunnerJobs.REPORT_JOB)
    Job reportJob;
    @Autowired
    ObjectProvider<JobParameterSpecRegistry> specs;

    @BeforeEach
    void clear() {
        capture.events.clear();
        RunnerJobs.FAILURE.set("");
    }

    private static String kind(BatchRunEvent event) {
        return switch (event) {
            case BatchRunEvent.Started started -> "started";
            case BatchRunEvent.Completed completed -> "completed";
            case BatchRunEvent.Failed failed -> "failed";
            case BatchRunEvent.Stopped stopped -> "stopped";
            case BatchRunEvent.Skipped skipped -> "skipped";
            case BatchRunEvent.Recovered recovered -> "recovered";
        };
    }

    private JobExecution runReport(String date) throws Exception {
        return jobOperator.start(reportJob, new JobParametersBuilder()
                .addLocalDate("businessDate", LocalDate.parse(date)).addString("region", "sg")
                .addString("note", "call 555-0100", false).addString("dbPassword", "hunter2", false)
                .toJobParameters());
    }

    @Test
    @DisplayName("a completed run publishes Started and Completed, sensitive and secret-like values masked")
    void completedRun() throws Exception {
        JobExecution execution = runReport("2026-11-03");

        assertThat(capture.events).extracting(BatchRunEventTest::kind).containsExactly("started", "completed");
        BatchRunEvent.Completed completed = (BatchRunEvent.Completed) capture.events.get(1);
        assertThat(completed.getJobName()).isEqualTo(RunnerJobs.REPORT_JOB);
        assertThat(completed.getExecutionId()).isEqualTo(execution.getId());
        assertThat(completed.getInstanceId()).isEqualTo(execution.getJobInstance().getInstanceId());
        assertThat(completed.getDuration()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
        assertThat(completed.getParameters()).containsEntry("businessDate", "2026-11-03")
                .containsEntry("region", "sg").containsEntry("note", "****").containsEntry("dbPassword", "****");
    }

    @Test
    @DisplayName("a failed run publishes Failed with its exit code and failures (and BatchJobFailedEvent still)")
    void failedRun() throws Exception {
        RunnerJobs.FAILURE.set("fatal");

        runReport("2026-11-04");

        assertThat(capture.events).extracting(BatchRunEventTest::kind).containsExactly("started", "failed");
        BatchRunEvent.Failed failed = (BatchRunEvent.Failed) capture.events.get(1);
        assertThat(failed.getExitCode()).isEqualTo(3);
        assertThat(failed.getFailures()).anyMatch(text -> text.contains("control totals differ"));
    }

    @Test
    @DisplayName("Stopped, Skipped and Recovered; a listener that throws never fails the run")
    void otherEvents() {
        List<BatchRunEvent> seen = new CopyOnWriteArrayList<>();
        BatchRunEventPublisher publisher = new BatchRunEventPublisher(event -> {
            seen.add((BatchRunEvent) event);
            throw new IllegalStateException("listener bug");
        }, specs);
        JobExecution execution = new JobExecution(7L, new JobInstance(3L, RunnerJobs.REPORT_JOB),
                new JobParametersBuilder().addString("note", "secret-ish", false).toJobParameters());
        execution.setStatus(BatchStatus.STOPPED);
        execution.setStartTime(LocalDateTime.now());

        publisher.jobFinished(execution);
        publisher.skipped(RunnerJobs.REPORT_JOB, 3L, null, new JobParameters(), "duplicate");
        publisher.recovered(execution, "its owner pod is gone");
        execution.setStatus(BatchStatus.STARTED);
        publisher.jobFinished(execution);

        assertThat(seen).extracting(BatchRunEventTest::kind).containsExactly("stopped", "skipped", "recovered");
        assertThat(((BatchRunEvent.Skipped) seen.get(1)).getReason()).isEqualTo("duplicate");
        assertThat(((BatchRunEvent.Skipped) seen.get(1)).getExecutionId()).isNull();
        assertThat(((BatchRunEvent.Recovered) seen.get(2)).getReason()).contains("gone");
        assertThat(seen.get(2).getParameters()).containsEntry("note", "****");
        assertThat(publisher.parameters("x", null)).isEmpty();
    }
}
