package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.fault.DeadLetterSkipListener;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.step.StepExecution;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Termination summary v1 (design 0025 §5.13, D13): the exact shape the Console's and the CLI's
 * fixtures hold, and the 3,584-byte cap that drops step detail first.
 */
class TerminationSummaryWriterTest {

    private static final String DIGEST = "sha256:7f3c1a9d2e4b5f60718293a4b5c6d7e8f9001122334455667788990011223344";
    private static final Instant START = Instant.parse("2026-11-03T17:30:04Z");
    private static final Instant END = Instant.parse("2026-11-03T17:43:36Z");

    /** The Console's {@code testdata/batch/summary-v1/completed.json} (design 0025 §5.13). */
    private static final String COMPLETED = "{\"v\":1,\"job\":\"importJob\",\"batchJob\":\"importer\",\"instanceId\":42,"
            + "\"executionId\":97,\"status\":\"COMPLETED\",\"exitCode\":0,\"exitDescription\":\"\","
            + "\"outcome\":\"completed\",\"trigger\":\"schedule\",\"businessDate\":\"2026-11-03\",\"read\":120000,"
            + "\"written\":119990,\"skipped\":10,\"deadLettered\":10,\"filtered\":0,\"start\":\"2026-11-03T17:30:04Z\","
            + "\"end\":\"2026-11-03T17:43:36Z\",\"image\":\"" + DIGEST + "\",\"steps\":[{\"name\":\"importStep\","
            + "\"status\":\"COMPLETED\",\"read\":120000,\"written\":119990,\"skipped\":10}]}";

    static RunContext context(RunTrigger trigger) {
        return new RunContext("importJob", "importer", BatchRole.SINGLE, trigger, "", "importer-1", "", "pod", "", "",
                Optional.of(OffsetDateTime.parse("2026-11-03T17:30:00Z")), ZoneId.of("UTC"),
                Optional.of(LocalDate.of(2026, 11, 3)), DIGEST, 1, OptionalInt.empty(), "c");
    }

    private static LocalDateTime local(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    static JobExecution execution(BatchStatus status, int steps) {
        JobExecution execution = new JobExecution(97L, new JobInstance(42L, "importJob"), new JobParameters());
        execution.setStatus(status);
        execution.setExitStatus(new ExitStatus(status.name(), ""));
        execution.setStartTime(local(START));
        execution.setEndTime(local(END));
        for (int i = 0; i < steps; i++) {
            StepExecution step = new StepExecution(100L + i, steps == 1 ? "importStep" : "step" + i, execution);
            step.setStatus(status);
            step.setReadCount(120000);
            step.setWriteCount(119990);
            step.setProcessSkipCount(10);
            step.getExecutionContext().putLong(DeadLetterSkipListener.COUNT_KEY, 10);
            execution.addStepExecution(step);
        }
        return execution;
    }

    private static String render(RunOutcome outcome, RunContext context) {
        return TerminationSummaryWriter.render(outcome, context, context == null ? Optional.empty()
                : context.businessDate(), START, END);
    }

    @Test
    @DisplayName("an ops run names its command, the execution it acted on and its counts, with a reason and no items")
    void opsCommand() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("replayed", 3L);
        counts.put("rejected", 1L);
        RunOutcome.Command command = RunOutcome.Command.on("dlq-replay", execution(BatchStatus.COMPLETED, 1))
                .counting(counts);
        String summary = render(RunOutcome.command(RunOutcome.Kind.FAILED, "rejected", 3, command,
                "1 item(s) of execution 97 are still rejected"), context(RunTrigger.OPS));

        assertThat(summary).isEqualTo("{\"v\":1,\"job\":\"importJob\",\"batchJob\":\"importer\",\"instanceId\":42,"
                + "\"executionId\":97,\"status\":\"COMPLETED\",\"exitCode\":3,\"exitDescription\":\"1 item(s) of "
                + "execution 97 are still rejected\",\"outcome\":\"failed\",\"reason\":\"rejected\",\"trigger\":\"ops\","
                + "\"command\":\"dlq-replay\",\"replayed\":3,\"rejected\":1,\"businessDate\":\"2026-11-03\","
                + "\"start\":\"2026-11-03T17:30:04Z\",\"end\":\"2026-11-03T17:43:36Z\",\"image\":\"" + DIGEST + "\"}");
        String none = render(RunOutcome.command(RunOutcome.Kind.INVALID, "unknown-command", 2,
                new RunOutcome.Command("explode", null, null, null, null), "not a command"), context(RunTrigger.OPS));
        assertThat(none).doesNotContain("executionId", "instanceId", "\"status\"").contains("\"command\":\"explode\"");
    }

    @Test
    @DisplayName("a completed run is exactly the design's example, byte for byte")
    void completed() {
        assertThat(render(RunOutcome.of(execution(BatchStatus.COMPLETED, 1)), context(RunTrigger.SCHEDULE)))
                .isEqualTo(COMPLETED);
    }

    @Test
    @DisplayName("a partitioned run counts its partitions once: the manager step's sums are not added again")
    void partitionedCountsOnce() throws Exception {
        JobExecution execution = execution(BatchStatus.COMPLETED, 0);
        StepExecution manager = new StepExecution(100L, "importManager", execution);
        manager.setStatus(BatchStatus.COMPLETED);
        manager.setReadCount(30);
        manager.setWriteCount(30);
        manager.getExecutionContext().put(org.springframework.batch.core.step.Step.STEP_TYPE_KEY,
                org.springframework.batch.core.partition.PartitionStep.class.getName());
        execution.addStepExecution(manager);
        for (int i = 0; i < 3; i++) {
            StepExecution partition = new StepExecution(101L + i, "importStep:partition" + i, execution);
            partition.setStatus(BatchStatus.COMPLETED);
            partition.setReadCount(10);
            partition.setWriteCount(10);
            execution.addStepExecution(partition);
        }

        JsonNode json = new ObjectMapper().readTree(render(RunOutcome.of(execution), context(RunTrigger.MANUAL)));

        assertThat(json.get("read").asLong()).isEqualTo(30);
        assertThat(json.get("written").asLong()).isEqualTo(30);
        assertThat(json.get("steps")).hasSize(4);
    }

    @Test
    @DisplayName("a skipped run has a reason and its instance, but no execution, status or counts")
    void skipped() throws Exception {
        String summary = render(RunOutcome.skipped(RunOutcome.DUPLICATE, 0, 42L, null, "pod x is running it"),
                context(RunTrigger.MANUAL));
        JsonNode json = new ObjectMapper().readTree(summary);

        assertThat(json.get("outcome").asText()).isEqualTo("skipped");
        assertThat(json.get("reason").asText()).isEqualTo("duplicate");
        assertThat(json.get("instanceId").asLong()).isEqualTo(42);
        assertThat(json.has("executionId")).isFalse();
        assertThat(json.has("status")).isFalse();
        assertThat(json.has("read")).isFalse();
        assertThat(json.get("start").asText()).isEqualTo("2026-11-03T17:30:04Z");
    }

    @Test
    @DisplayName("an invalid run: reason, the message as the description, no instance; and without a context")
    void invalid() throws Exception {
        JsonNode json = new ObjectMapper().readTree(render(RunOutcome.invalid("parameters",
                "Invalid parameters for job [importJob]: region must be one of [sg, my], was 'xx'"),
                context(RunTrigger.MANUAL)));

        assertThat(json.get("exitCode").asInt()).isEqualTo(2);
        assertThat(json.get("reason").asText()).isEqualTo("parameters");
        assertThat(json.get("exitDescription").asText()).endsWith("was 'xx'");
        assertThat(json.has("instanceId")).isFalse();

        JsonNode bare = new ObjectMapper().readTree(render(RunOutcome.failed(1, "boom\n\tat x.y(Z.java:1)"), null));
        assertThat(bare.get("job").asText()).isEmpty();
        assertThat(bare.get("exitDescription").asText()).as("the first line only").isEqualTo("boom");
        assertThat(bare.has("image")).isFalse();
    }

    @Test
    @DisplayName("over 3,584 bytes the steps go first, flagged stepsTruncated; the totals stay")
    void stepsDroppedFirst() throws Exception {
        String summary = render(RunOutcome.of(execution(BatchStatus.COMPLETED, 60)), context(RunTrigger.SCHEDULE));
        JsonNode json = new ObjectMapper().readTree(summary);

        assertThat(summary.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(TerminationSummaryWriter.MAX_BYTES);
        assertThat(json.has("steps")).isFalse();
        assertThat(json.get("stepsTruncated").asBoolean()).isTrue();
        assertThat(json.get("read").asLong()).isEqualTo(60 * 120000L);
    }

    @Test
    @DisplayName("then the description is cut, by bytes, never inside a surrogate pair")
    void descriptionCut() throws Exception {
        String message = "\u0001".repeat(500) + "🚀".repeat(400);
        String summary = render(RunOutcome.failed(5, message), context(RunTrigger.SCHEDULE));

        assertThat(summary.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(TerminationSummaryWriter.MAX_BYTES)
                .isGreaterThan(TerminationSummaryWriter.MAX_BYTES - 8);
        assertThat(new ObjectMapper().readTree(summary).get("exitDescription").asText()).startsWith("\u0001")
                .endsWith("🚀");
        assertThat(render(RunOutcome.failed(5, "x".repeat(5000)), null)).contains("\"" + "x".repeat(1024) + "\"");
    }

    @Test
    @DisplayName("it is written to the termination log and to stdout as one line; without a log directory, stdout only")
    void written(@TempDir Path dir) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Path log = dir.resolve("termination-log");

        String summary = new TerminationSummaryWriter(log, new PrintStream(out, true, StandardCharsets.UTF_8))
                .write(RunOutcome.of(execution(BatchStatus.COMPLETED, 1)), context(RunTrigger.SCHEDULE),
                        Optional.of(LocalDate.of(2026, 11, 3)), START, END);

        assertThat(summary).isEqualTo(COMPLETED);
        assertThat(Files.readString(log)).isEqualTo(COMPLETED);
        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(COMPLETED + System.lineSeparator());
        new TerminationSummaryWriter(dir.resolve("missing/termination-log"), new PrintStream(out))
                .write(RunOutcome.failed(1, "x"), null, Optional.empty(), START, END);
        assertThat(dir.resolve("missing")).doesNotExist();
    }
}
