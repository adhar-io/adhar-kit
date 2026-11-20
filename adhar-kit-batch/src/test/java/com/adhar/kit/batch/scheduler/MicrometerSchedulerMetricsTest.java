package com.adhar.kit.batch.scheduler;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * KIT-Q.7 (design 0025 §5.20.8): the {@code adhar.batch.scheduler.*} meters, on a meter registry.
 */
class MicrometerSchedulerMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerSchedulerMetrics metrics = new MicrometerSchedulerMetrics(registry);

    @Test
    @DisplayName("every outcome of either engine maps onto the six tags of adhar.batch.scheduler.fires")
    void outcomeTags() {
        Map<SlotOutcome, String> tags = new TreeMap<>();
        Arrays.stream(SlotOutcome.values()).forEach(outcome -> tags.put(outcome, SchedulerMetrics.outcomeTag(outcome)));

        assertThat(tags).containsExactly(
                Map.entry(SlotOutcome.RAN, "ran"),
                Map.entry(SlotOutcome.MISFIRED, "misfire-skipped"),
                Map.entry(SlotOutcome.OVERLAPPING, "skipped-duplicate"),
                Map.entry(SlotOutcome.LOCKED_ELSEWHERE, "skipped-duplicate"),
                Map.entry(SlotOutcome.ALREADY_RAN, "skipped-complete"),
                Map.entry(SlotOutcome.FAILED_TO_START, "failed-to-start"),
                Map.entry(SlotOutcome.RECOVERED, "recovered"),
                Map.entry(SlotOutcome.DUPLICATE, "skipped-duplicate"),
                Map.entry(SlotOutcome.ABANDONED, "failed-to-start"),
                Map.entry(SlotOutcome.BEYOND_CATCH_UP, "misfire-skipped"));
    }

    @Test
    @DisplayName("fires count per job, trigger and outcome")
    void firesCount() {
        metrics.fired("import-job", "nightly", SlotOutcome.RAN);
        metrics.fired("import-job", "nightly", SlotOutcome.RAN);
        metrics.fired("import-job", "nightly", SlotOutcome.DUPLICATE);
        metrics.fired("import-job", "now", SlotOutcome.RECOVERED);

        assertThat(registry.get(SchedulerMetrics.FIRES).tags("job", "import-job", "trigger", "nightly", "outcome", "ran")
                .counter().count()).isEqualTo(2.0);
        assertThat(registry.get(SchedulerMetrics.FIRES).tags("outcome", "skipped-duplicate").counter().count())
                .isEqualTo(1.0);
        assertThat(registry.get(SchedulerMetrics.FIRES).tags("trigger", "now", "outcome", "recovered").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("the next fire is a gauge in epoch seconds per job and trigger, removed when the trigger has none")
    void nextFireGauge() {
        metrics.nextFire("import-job", "nightly", Instant.parse("2026-11-16T17:30:00Z"));
        metrics.nextFire("import-job", "nightly", Instant.parse("2026-11-17T17:30:00Z"));

        assertThat(registry.get(SchedulerMetrics.NEXT_FIRE).tags("job", "import-job", "trigger", "nightly").gauge().value())
                .isEqualTo(Instant.parse("2026-11-17T17:30:00Z").getEpochSecond());
        assertThat(registry.find(SchedulerMetrics.NEXT_FIRE).gauges()).hasSize(1);

        metrics.nextFire("import-job", "nightly", null);
        metrics.nextFire("import-job", "never-registered", null);

        assertThat(registry.find(SchedulerMetrics.NEXT_FIRE).gauges()).isEmpty();
    }

    @Test
    @DisplayName("instances, report failures and calendar failures")
    void instancesAndFailures() {
        metrics.instances(3);
        metrics.reportFailed();
        metrics.reportFailed();
        metrics.calendarFailed("sg-business");

        assertThat(registry.get(SchedulerMetrics.INSTANCES).gauge().value()).isEqualTo(3.0);
        assertThat(registry.get(SchedulerMetrics.REPORT_FAILURES).counter().count()).isEqualTo(2.0);
        assertThat(registry.get(SchedulerMetrics.CALENDAR_FAILURES).tags("calendar", "sg-business").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("without a registry the engines record into metrics that do nothing")
    void none() {
        SchedulerMetrics none = SchedulerMetrics.none();

        assertThatCode(() -> {
            none.fired("a", "b", SlotOutcome.RAN);
            none.nextFire("a", "b", Instant.EPOCH);
            none.instances(1);
            none.reportFailed();
            none.calendarFailed("c");
        }).doesNotThrowAnyException();
        assertThat(SchedulerMetrics.none()).isSameAs(none);
    }
}
