package com.adhar.adharkit.logging.batch;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.event.RecordingAppLogEventSink;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BatchJobLogger} and {@link BatchJobRun}.
 */
class BatchJobLoggerTest {

    private AdharLoggingProperties properties;
    private RecordingAppLogEventSink sink;
    private BatchJobLogger batchJobLogger;

    @BeforeEach
    void setUp() {
        properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        batchJobLogger = new BatchJobLogger(properties,
                new AppLogEventPublisher(properties, new LogDataMasker(properties.getMasking()),
                        List.of(sink)));
    }

    @Test
    void startJobPublishesStartedEvent() {
        BatchJobRun run = batchJobLogger.startJob("nightly-job");

        assertThat(run.getJobName()).isEqualTo("nightly-job");
        assertThat(run.getJobId()).isNotBlank();
        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.BATCH);
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.STARTED);
        assertThat(event.getMetadata()).containsEntry("jobId", run.getJobId());
    }

    @Test
    void eventsOfARunWithAContextAreLoggedWithItInTheMdcFromAnyThread() throws Exception {
        List<Map<String, String>> mdcAtLog = new CopyOnWriteArrayList<>();
        BatchJobLogger logger = new BatchJobLogger(properties, new AppLogEventPublisher(properties,
                new LogDataMasker(properties.getMasking()), List.of(event -> mdcAtLog.add(mdc()))));
        MDC.clear(); // other tests of this JVM leave entries on the main thread
        MDC.put("batch.step", "outer");

        BatchJobRun run = logger.startJob("importJob", Map.of("batch.job", "importJob", "batch.execution", "97",
                "batch.step", "fromContext"));
        Thread worker = new Thread(() -> run.startStep("worker:partition1"));
        worker.start();
        worker.join();
        run.complete();

        assertThat(mdcAtLog).hasSize(4).allSatisfy(entries -> assertThat(entries)
                .containsEntry("batch.job", "importJob").containsEntry("batch.execution", "97"));
        assertThat(mdcAtLog.getFirst()).containsEntry("batch.step", "outer");
        assertThat(mdcAtLog.get(1)).containsEntry("batch.step", "fromContext");
        assertThat(mdc()).containsExactly(Map.entry("batch.step", "outer"));
        assertThat(run.getContext()).hasSize(3);
        MDC.clear();
    }

    private static Map<String, String> mdc() {
        Map<String, String> copy = MDC.getCopyOfContextMap();
        return copy == null ? Map.of() : copy;
    }

    @Test
    void completePublishesSummaryWithCountersAndRate() {
        BatchJobRun run = batchJobLogger.startJob("job");
        run.itemProcessed();
        run.itemProcessed();
        run.itemSkipped();
        run.complete();

        AppLogEvent summary = sink.last();
        assertThat(summary.getMessage()).isEqualTo("COMPLETED");
        assertThat(summary.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(summary.getDurationMs()).isNotNull();
        assertThat(summary.getMetadata())
                .containsEntry("itemsProcessed", 2L)
                .containsEntry("itemsSkipped", 1L)
                .containsEntry("itemsFailed", 0L)
                .containsKey("itemsPerSecond");
        assertThat(run.getItemsProcessed()).isEqualTo(2);
        assertThat(run.getItemsSkipped()).isEqualTo(1);
    }

    @Test
    void completeWithItemFailuresIsPartial() {
        BatchJobRun run = batchJobLogger.startJob("job");
        run.itemProcessed();
        run.itemFailed(new RuntimeException("bad row"), "item-9");
        run.complete();

        AppLogEvent summary = sink.last();
        assertThat(summary.getOutcome()).isEqualTo(AppLogEventOutcome.PARTIAL);
        assertThat(summary.getSeverity()).isEqualTo(Level.WARN);
        assertThat(run.getItemsFailed()).isEqualTo(1);
    }

    @Test
    void failPublishesFailureSummary() {
        BatchJobRun run = batchJobLogger.startJob("job");
        run.fail(new IllegalStateException("db down"));

        AppLogEvent summary = sink.last();
        assertThat(summary.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(summary.getSeverity()).isEqualTo(Level.ERROR);
        assertThat(summary.getErrorMessage()).isEqualTo("db down");
    }

    @Test
    void closeCompletesUnfinishedRunAndIsIdempotent() {
        try (BatchJobRun run = batchJobLogger.startJob("job")) {
            run.itemProcessed();
        }
        long completedEvents = sink.getEvents().stream()
                .filter(e -> "COMPLETED".equals(e.getMessage()))
                .count();
        assertThat(completedEvents).isEqualTo(1);
    }

    @Test
    void closeAfterFailDoesNotDoubleFinish() {
        BatchJobRun run = batchJobLogger.startJob("job");
        run.fail(new RuntimeException("x"));
        run.close();

        long finishEvents = sink.getEvents().stream()
                .filter(e -> "COMPLETED".equals(e.getMessage()))
                .count();
        assertThat(finishEvents).isEqualTo(1);
    }

    @Test
    void stepsPublishStartAndCompleteEvents() {
        BatchJobRun run = batchJobLogger.startJob("job");
        run.startStep("extract");
        run.startStep("load");
        run.complete();

        List<String> messages = sink.getEvents().stream().map(AppLogEvent::getMessage).toList();
        assertThat(messages).containsSubsequence("STEP_STARTED", "STEP_COMPLETED", "STEP_STARTED",
                "STEP_COMPLETED", "COMPLETED");
    }

    @Test
    void progressEventEmittedAtConfiguredInterval() {
        properties.getBatch().setProgressLogInterval(5);
        BatchJobRun run = batchJobLogger.startJob("job");
        for (int i = 0; i < 12; i++) {
            run.itemProcessed();
        }

        long progressEvents = sink.getEvents().stream()
                .filter(e -> "PROGRESS".equals(e.getMessage()))
                .count();
        assertThat(progressEvents).isEqualTo(2);
    }

    @Test
    void itemErrorLoggingIsCapped() {
        properties.getBatch().setMaxItemErrorsLogged(3);
        BatchJobRun run = batchJobLogger.startJob("job");
        for (int i = 0; i < 10; i++) {
            run.itemFailed(new RuntimeException("row " + i), "item-" + i);
        }

        long itemFailedEvents = sink.getEvents().stream()
                .filter(e -> "ITEM_FAILED".equals(e.getMessage()))
                .count();
        assertThat(itemFailedEvents).isEqualTo(3);
        assertThat(run.getItemsFailed()).isEqualTo(10);
    }

    @Test
    void disabledBatchLoggingPublishesNoEvents() {
        properties.getBatch().setEnabled(false);
        BatchJobRun run = batchJobLogger.startJob("job");
        run.itemProcessed();
        run.complete();

        assertThat(sink.getEvents()).isEmpty();
    }
}
