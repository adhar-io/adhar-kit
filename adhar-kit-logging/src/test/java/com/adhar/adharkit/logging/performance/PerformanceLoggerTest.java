package com.adhar.adharkit.logging.performance;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.event.RecordingAppLogEventSink;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PerformanceLogger}, {@link PerformanceTimer} and {@link OperationStats}.
 */
class PerformanceLoggerTest {

    private AdharLoggingProperties properties;
    private RecordingAppLogEventSink sink;
    private PerformanceLogger performanceLogger;

    @BeforeEach
    void setUp() {
        properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        performanceLogger = new PerformanceLogger(properties,
                new AppLogEventPublisher(properties, new LogDataMasker(properties.getMasking()),
                        List.of(sink)));
    }

    @Test
    void recordAccumulatesStatistics() {
        performanceLogger.record("op", 100, true);
        performanceLogger.record("op", 200, true);
        performanceLogger.record("op", 300, false);

        OperationStats.Snapshot snap = performanceLogger.snapshot().get("op");
        assertThat(snap.count()).isEqualTo(3);
        assertThat(snap.failures()).isEqualTo(1);
        assertThat(snap.totalMs()).isEqualTo(600);
        assertThat(snap.minMs()).isEqualTo(100);
        assertThat(snap.maxMs()).isEqualTo(300);
        assertThat(snap.avgMs()).isEqualTo(200.0);
    }

    @Test
    void fastOperationsAreNotPublishedByDefault() {
        performanceLogger.record("op", 10, true);
        assertThat(sink.getEvents()).isEmpty();
    }

    @Test
    void slowOperationPublishesWarnEvent() {
        performanceLogger.record("op", 5000, true);

        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.PERFORMANCE);
        assertThat(event.getSeverity()).isEqualTo(Level.WARN);
        assertThat(event.getMessage()).isEqualTo("Slow operation detected");
        assertThat(event.getMetadata()).containsEntry("slow", true);
        assertThat(event.getDurationMs()).isEqualTo(5000L);
    }

    @Test
    void logAllOperationsPublishesDebugEvents() {
        properties.getPerformance().setLogAllOperations(true);
        performanceLogger.record("op", 10, true);

        AppLogEvent event = sink.last();
        assertThat(event.getSeverity()).isEqualTo(Level.DEBUG);
        assertThat(event.getMetadata()).containsEntry("slow", false);
    }

    @Test
    void failedSlowOperationHasFailureOutcome() {
        performanceLogger.record("op", 5000, false);
        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
    }

    @Test
    void explicitThresholdOverridesGlobal() {
        performanceLogger.record("op", 50, true, 20);
        assertThat(sink.last().getMetadata()).containsEntry("thresholdMs", 20L);
        assertThat(sink.last().getSeverity()).isEqualTo(Level.WARN);
    }

    @Test
    void timerRecordsDurationAndSuccessFlag() {
        try (PerformanceTimer timer = performanceLogger.start("timed-op")) {
            assertThat(timer.elapsedMs()).isGreaterThanOrEqualTo(0);
        }
        assertThat(performanceLogger.snapshot().get("timed-op").count()).isEqualTo(1);
        assertThat(performanceLogger.snapshot().get("timed-op").failures()).isZero();

        PerformanceTimer failing = performanceLogger.start("timed-op");
        failing.failure();
        failing.close();
        failing.close(); // idempotent
        OperationStats.Snapshot snap = performanceLogger.snapshot().get("timed-op");
        assertThat(snap.count()).isEqualTo(2);
        assertThat(snap.failures()).isEqualTo(1);
    }

    @Test
    void logSummaryPublishesOneEventPerOperation() {
        performanceLogger.record("op-a", 10, true);
        performanceLogger.record("op-b", 20, false);
        sink.clear();

        performanceLogger.logSummary();

        assertThat(sink.getEvents()).hasSize(2);
        AppLogEvent event = sink.getEvents().get(0);
        assertThat(event.getCategory()).isEqualTo("performance-summary");
        assertThat(event.getMetadata()).containsKeys("count", "failures", "totalMs", "minMs", "maxMs", "avgMs");
    }

    @Test
    void resetClearsStatistics() {
        performanceLogger.record("op", 10, true);
        performanceLogger.reset();
        assertThat(performanceLogger.snapshot()).isEmpty();
    }

    @Test
    void disabledPerformanceLoggingIsNoOp() {
        properties.getPerformance().setEnabled(false);
        performanceLogger.record("op", 5000, true);
        performanceLogger.logSummary();

        assertThat(sink.getEvents()).isEmpty();
        assertThat(performanceLogger.snapshot()).isEmpty();
    }

    @Test
    void emptyStatsSnapshotIsZeroed() {
        OperationStats stats = new OperationStats();
        OperationStats.Snapshot snap = stats.snapshot();
        assertThat(snap.count()).isZero();
        assertThat(snap.minMs()).isZero();
        assertThat(snap.maxMs()).isZero();
        assertThat(snap.avgMs()).isZero();
    }
}
