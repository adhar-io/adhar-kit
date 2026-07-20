package com.adhar.kit.metrics.slo;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link SloRecorder} error-budget and burn-rate math.
 */
class SloRecorderTest {

    private SimpleMeterRegistry registry;
    private AtomicLong clock;
    private SloRecorder recorder;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        clock = new AtomicLong(1_000_000_000L);

        AdharMetricsProperties.SloProperties props = new AdharMetricsProperties.SloProperties();
        props.setTargets(Map.of(
                "checkout", 0.999,
                SloRecorder.GLOBAL_TARGET, 0.99));
        props.setWindowSeconds(3600);

        recorder = new SloRecorder(registry, props, clock::get);
    }

    @Test
    void registersGaugesPerConfiguredTarget() {
        assertThat(registry.find(SloRecorder.ERROR_BUDGET_METRIC).tag("target", "checkout").gauge()).isNotNull();
        assertThat(registry.find(SloRecorder.BURN_RATE_METRIC).tag("target", "checkout").gauge()).isNotNull();
        assertThat(registry.find(SloRecorder.ERROR_BUDGET_METRIC).tag("target", "global").gauge()).isNotNull();
        assertThat(registry.find(SloRecorder.BURN_RATE_METRIC).tag("target", "global").gauge()).isNotNull();
    }

    @Test
    void noTraffic_fullBudgetAndZeroBurn() {
        assertThat(recorder.errorBudgetRemaining("checkout")).isEqualTo(1.0);
        assertThat(recorder.burnRate("checkout")).isEqualTo(0.0);
    }

    @Test
    void allSuccesses_keepFullBudget() {
        for (int i = 0; i < 1000; i++) {
            recorder.record("checkout", true);
        }

        assertThat(recorder.errorBudgetRemaining("checkout")).isEqualTo(1.0);
        assertThat(recorder.burnRate("checkout")).isEqualTo(0.0);
    }

    @Test
    void burnRateOne_whenErrorRateEqualsAllowedRate() {
        // objective 0.999 -> allowed error rate 0.001; 1 error in 1000 = exactly at budget
        for (int i = 0; i < 999; i++) {
            recorder.record("checkout", true);
        }
        recorder.record("checkout", false);

        assertThat(recorder.burnRate("checkout")).isCloseTo(1.0, within(1e-9));
        assertThat(recorder.errorBudgetRemaining("checkout")).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void halfBudgetConsumed_atHalfAllowedErrorRate() {
        // objective 0.99 -> allowed error rate 0.01; 1 error in 200 = 0.005 observed
        for (int i = 0; i < 199; i++) {
            recorder.record("global", true);
        }
        recorder.record("global", false);

        assertThat(recorder.burnRate("global")).isCloseTo(0.5, within(1e-9));
        assertThat(recorder.errorBudgetRemaining("global")).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void budgetIsClampedAtZeroWhenOverspent() {
        // 10% errors against a 0.1% allowance
        for (int i = 0; i < 90; i++) {
            recorder.record("checkout", true);
        }
        for (int i = 0; i < 10; i++) {
            recorder.record("checkout", false);
        }

        assertThat(recorder.errorBudgetRemaining("checkout")).isEqualTo(0.0);
        assertThat(recorder.burnRate("checkout")).isCloseTo(100.0, within(1e-6));
    }

    @Test
    void gaugesExposeComputedValues() {
        for (int i = 0; i < 199; i++) {
            recorder.record("global", true);
        }
        recorder.record("global", false);

        assertThat(registry.find(SloRecorder.ERROR_BUDGET_METRIC).tag("target", "global").gauge().value())
                .isCloseTo(0.5, within(1e-9));
        assertThat(registry.find(SloRecorder.BURN_RATE_METRIC).tag("target", "global").gauge().value())
                .isCloseTo(0.5, within(1e-9));
    }

    @Test
    void rollingWindow_expiresOldOutcomes() {
        for (int i = 0; i < 10; i++) {
            recorder.record("checkout", false);
        }
        assertThat(recorder.burnRate("checkout")).isGreaterThan(1.0);

        // advance past the rolling window; the old errors must no longer count
        clock.addAndGet(3600_000L + 120_000L);
        assertThat(recorder.burnRate("checkout")).isEqualTo(0.0);
        assertThat(recorder.errorBudgetRemaining("checkout")).isEqualTo(1.0);

        // fresh traffic starts a new window
        recorder.record("checkout", true);
        assertThat(recorder.errorBudgetRemaining("checkout")).isEqualTo(1.0);
    }

    @Test
    void recordHttp_globalTargetMatchesEverything() {
        recorder.recordHttp("/anything/else", false);

        assertThat(recorder.burnRate("global")).isGreaterThan(0.0);
        assertThat(recorder.burnRate("checkout")).isEqualTo(0.0);
    }

    @Test
    void recordHttp_endpointTargetMatchesUriSegment() {
        recorder.recordHttp("/api/checkout", false);

        assertThat(recorder.burnRate("checkout")).isGreaterThan(0.0);
        assertThat(recorder.burnRate("global")).isGreaterThan(0.0);
    }

    @Test
    void recordHttp_matchesExactAndNestedForms() {
        recorder.recordHttp("checkout", true);
        recorder.recordHttp("/checkout", true);
        recorder.recordHttp("/api/checkout/items", true);
        recorder.recordHttp("/api/checkouts", false); // must NOT match "checkout"
        recorder.recordHttp(null, false);             // only global matches

        // the two errors above were not attributed to the checkout target
        assertThat(recorder.errorBudgetRemaining("checkout")).isEqualTo(1.0);
        assertThat(recorder.burnRate("checkout")).isEqualTo(0.0);
        assertThat(recorder.burnRate("global")).isGreaterThan(0.0);
    }

    @Test
    void unknownTarget_isIgnoredSafely() {
        recorder.record("does-not-exist", false);

        assertThat(recorder.errorBudgetRemaining("does-not-exist")).isEqualTo(1.0);
        assertThat(recorder.burnRate("does-not-exist")).isEqualTo(0.0);
    }

    @Test
    void objectivesAreExposed() {
        assertThat(recorder.getObjectives())
                .containsEntry("checkout", 0.999)
                .containsEntry("global", 0.99);
    }
}
