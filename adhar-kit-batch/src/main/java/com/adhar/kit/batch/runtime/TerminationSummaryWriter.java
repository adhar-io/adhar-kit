package com.adhar.kit.batch.runtime;

import com.adhar.kit.batch.fault.DeadLetterSkipListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * Writes a run's termination summary v1 (design 0025 §5.13, D13) to the container's termination
 * message ({@code /dev/termination-log}) and to stdout, where the Console, the CLI and the batch
 * observer read it.
 *
 * <pre>{@code
 * {"v":1,"job":"importJob","batchJob":"importer","instanceId":42,"executionId":97,"status":"COMPLETED",
 *  "exitCode":0,"exitDescription":"","outcome":"completed","trigger":"schedule","businessDate":"2026-11-03",
 *  "read":120000,"written":119990,"skipped":10,"deadLettered":10,"filtered":0,
 *  "start":"2026-11-03T17:30:04Z","end":"2026-11-03T17:43:36Z","image":"sha256:...",
 *  "steps":[{"name":"importStep","status":"COMPLETED","read":120000,"written":119990,"skipped":10}]}
 * }</pre>
 *
 * <p>{@code reason} is present for {@code skipped} and {@code invalid}; {@code instanceId},
 * {@code executionId}, {@code status} and the counts are absent when there is no execution. The
 * kubelet keeps at most 4096 bytes of a termination message, so the summary is held to
 * {@value #MAX_BYTES} bytes: step detail goes first ({@code "stepsTruncated":true}), then the end
 * of {@code exitDescription}. The exit description is its first line: Spring Batch puts whole
 * stack traces there, which belong in the log.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class TerminationSummaryWriter {

    /** Where Kubernetes reads a container's termination message by default. */
    public static final Path TERMINATION_LOG = Path.of("/dev/termination-log");

    /** The largest summary written, in UTF-8 bytes. */
    public static final int MAX_BYTES = 3584;

    /** The longest exit description kept before the size cap applies. */
    static final int MAX_DESCRIPTION = 1024;

    private final Path terminationLog;
    private final PrintStream stdout;

    /**
     * @param terminationLog the termination message file; nothing is written there when its
     *                       directory does not exist (outside a container)
     * @param stdout         where the summary is also printed, as one line
     */
    public TerminationSummaryWriter(Path terminationLog, PrintStream stdout) {
        this.terminationLog = terminationLog;
        this.stdout = stdout;
    }

    /**
     * @param outcome      how the run ended
     * @param context      the run's context, {@code null} when it could not be resolved
     * @param businessDate the run's business date
     * @param start        when the run began, for a run without an execution
     * @param end          when it ended, likewise
     * @return the summary written
     */
    public String write(RunOutcome outcome, RunContext context, Optional<LocalDate> businessDate, Instant start,
                        Instant end) {
        String summary = render(outcome, context, businessDate, start, end);
        stdout.println(summary);
        stdout.flush();
        Path parent = terminationLog.toAbsolutePath().getParent();
        if (parent != null && Files.isDirectory(parent)) {
            try {
                Files.writeString(terminationLog, summary, StandardCharsets.UTF_8);
            } catch (IOException | SecurityException ex) {
                log.warn("Could not write the termination summary to {}: {}", terminationLog, ex.getMessage());
            }
        }
        return summary;
    }

    /**
     * @return the summary, at most {@value #MAX_BYTES} UTF-8 bytes
     */
    static String render(RunOutcome outcome, RunContext context, Optional<LocalDate> businessDate, Instant start,
                         Instant end) {
        String description = firstLine(outcome.message());
        description = cut(description, MAX_DESCRIPTION);
        String full = render(outcome, context, businessDate, start, end, description, true);
        if (bytes(full) <= MAX_BYTES) {
            return full;
        }
        String withoutSteps = render(outcome, context, businessDate, start, end, description, false);
        if (bytes(withoutSteps) <= MAX_BYTES) {
            return withoutSteps;
        }
        // The longest prefix of the description that fits: escapes and multi-byte characters
        // make its size in bytes no simple function of its length.
        int fits = 0;
        int tooLong = description.length();
        while (tooLong - fits > 1) {
            int middle = (fits + tooLong) >>> 1;
            String candidate = render(outcome, context, businessDate, start, end, cut(description, middle), false);
            if (bytes(candidate) <= MAX_BYTES) {
                fits = middle;
            } else {
                tooLong = middle;
            }
        }
        return render(outcome, context, businessDate, start, end, cut(description, fits), false);
    }

    private static String render(RunOutcome outcome, RunContext context, Optional<LocalDate> businessDate,
                                 Instant start, Instant end, String description, boolean withSteps) {
        JobExecution execution = outcome.execution();
        Fields json = new Fields();
        json.number("v", 1);
        String job = context != null ? context.jobName()
                : execution != null ? execution.getJobInstance().getJobName() : "";
        json.text("job", job);
        json.text("batchJob", context != null ? context.batchJobName() : job);
        Long instanceId = execution != null ? Long.valueOf(execution.getJobInstance().getInstanceId())
                : outcome.instanceId();
        if (instanceId != null) {
            json.number("instanceId", instanceId);
        }
        boolean counted = execution != null && outcome.kind() != RunOutcome.Kind.SKIPPED;
        if (counted) {
            json.number("executionId", execution.getId());
            json.text("status", execution.getStatus().name());
        }
        json.number("exitCode", outcome.exitCode());
        json.text("exitDescription", description);
        json.text("outcome", outcome.kind().label());
        if (outcome.reason() != null) {
            json.text("reason", outcome.reason());
        }
        json.text("trigger", context != null ? context.trigger().label() : "");
        businessDate.ifPresent(date -> json.text("businessDate", date.toString()));
        List<StepExecution> steps = counted ? steps(execution) : List.of();
        if (counted) {
            json.number("read", steps.stream().mapToLong(StepExecution::getReadCount).sum());
            json.number("written", steps.stream().mapToLong(StepExecution::getWriteCount).sum());
            json.number("skipped", steps.stream().mapToLong(StepExecution::getSkipCount).sum());
            json.number("deadLettered", steps.stream().mapToLong(TerminationSummaryWriter::deadLettered).sum());
            json.number("filtered", steps.stream().mapToLong(StepExecution::getFilterCount).sum());
        }
        json.text("start", time(counted ? execution.getStartTime() : null, start));
        json.text("end", time(counted ? execution.getEndTime() : null, end));
        if (context != null && !context.imageDigest().isBlank()) {
            json.text("image", context.imageDigest());
        }
        if (counted && withSteps) {
            StringJoiner array = new StringJoiner(",", "[", "]");
            for (StepExecution step : steps) {
                Fields entry = new Fields();
                entry.text("name", step.getStepName());
                entry.text("status", step.getStatus().name());
                entry.number("read", step.getReadCount());
                entry.number("written", step.getWriteCount());
                entry.number("skipped", step.getSkipCount());
                array.add(entry.toString());
            }
            json.raw("steps", array.toString());
        } else if (counted && !steps.isEmpty()) {
            json.raw("stepsTruncated", "true");
        }
        return json.toString();
    }

    private static List<StepExecution> steps(JobExecution execution) {
        List<StepExecution> steps = new ArrayList<>(execution.getStepExecutions());
        steps.sort(Comparator.comparingLong(StepExecution::getId));
        return steps;
    }

    private static long deadLettered(StepExecution step) {
        return step.getExecutionContext().getLong(DeadLetterSkipListener.COUNT_KEY, 0L);
    }

    private static String time(LocalDateTime recorded, Instant fallback) {
        Instant instant = recorded != null ? recorded.atZone(ZoneId.systemDefault()).toInstant() : fallback;
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }

    private static String firstLine(String text) {
        if (text == null) {
            return "";
        }
        int newline = text.indexOf('\n');
        return Json.sanitize((newline >= 0 ? text.substring(0, newline) : text).strip());
    }

    /** At most {@code length} characters, never ending inside a surrogate pair. */
    private static String cut(String text, int length) {
        if (text.length() <= length) {
            return text;
        }
        int end = Math.max(0, length);
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }

    private static int bytes(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }

    /** A JSON object written field by field, in order. */
    private static final class Fields {

        private final StringJoiner object = new StringJoiner(",", "{", "}");

        void text(String name, String value) {
            object.add(Json.quote(name) + ":" + Json.quote(value == null ? "" : value));
        }

        void number(String name, long value) {
            object.add(Json.quote(name) + ":" + value);
        }

        void raw(String name, String json) {
            object.add(Json.quote(name) + ":" + json);
        }

        @Override
        public String toString() {
            return object.toString();
        }
    }
}
