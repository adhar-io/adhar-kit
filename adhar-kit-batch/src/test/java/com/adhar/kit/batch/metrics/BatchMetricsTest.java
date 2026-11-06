package com.adhar.kit.batch.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.step.StepExecution;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchMetrics}.
 */
class BatchMetricsTest {

    private static final Instant NOW = Instant.parse("2026-11-06T12:00:00Z");

    private MeterRegistry meterRegistry;
    private BatchMetrics batchMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        batchMetrics = new BatchMetrics(meterRegistry, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private JobExecution execution(String trigger) {
        var parameters = new JobParametersBuilder();
        if (trigger != null) {
            parameters.addString(BatchMetrics.TRIGGER_PARAMETER, trigger, false);
        }
        return new JobExecution(1L, new JobInstance(1L, "importJob"), parameters.toJobParameters());
    }

    private double run(String trigger) {
        return meterRegistry.get(BatchMetrics.RUN).tag("job", "importJob").tag("trigger", trigger).gauge().value();
    }

    // ----------------------------------------------------------------
    // adhar.batch.run
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("adhar.batch.run")
    class Run {

        @Test
        @DisplayName("should be 1 while a run is in progress and 0 after it")
        void tracksARun() {
            var execution = execution("embedded");

            batchMetrics.jobStarted(execution);
            assertThat(run("embedded")).isEqualTo(1.0);

            batchMetrics.jobFinished(execution);
            assertThat(run("embedded")).isZero();
        }

        @Test
        @DisplayName("should never go below zero")
        void neverNegative() {
            var execution = execution("embedded");

            batchMetrics.jobStarted(execution);
            batchMetrics.jobFinished(execution);
            batchMetrics.jobFinished(execution);

            assertThat(run("embedded")).isZero();
        }

        @Test
        @DisplayName("should tag a run without a trigger as unknown")
        void missingTrigger() {
            batchMetrics.jobStarted(execution(null));

            assertThat(run("unknown")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should normalise a known trigger")
        void knownTrigger() {
            batchMetrics.jobStarted(execution(" Schedule "));

            assertThat(run("schedule")).isEqualTo(1.0);
        }

        @Test
        @DisplayName("should tag anything else as other, keeping the tag's cardinality bounded")
        void unknownTrigger() {
            batchMetrics.jobStarted(execution("2026-11-06-batch-42"));

            assertThat(run("other")).isEqualTo(1.0);
        }
    }

    // ----------------------------------------------------------------
    // adhar.batch.last.commit
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("adhar.batch.last.commit")
    class LastCommit {

        @Test
        @DisplayName("should hold the epoch seconds of the step's last committed chunk")
        void recordsTheCommitTime() {
            var step = mock(StepExecution.class);
            when(step.getJobExecution()).thenReturn(execution(null));
            when(step.getStepName()).thenReturn("step1");

            batchMetrics.chunkCommitted(step, 10);

            assertThat(meterRegistry.get(BatchMetrics.LAST_COMMIT).tag("job", "importJob").tag("step", "step1")
                    .gauge().value()).isEqualTo((double) NOW.getEpochSecond());
        }
    }

    // ----------------------------------------------------------------
    // adhar.batch.items.dead.lettered
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("adhar.batch.items.dead.lettered")
    class DeadLettered {

        @Test
        @DisplayName("should accumulate per job and step")
        void counts() {
            batchMetrics.recordDeadLettered("importJob", "step1", 2);
            batchMetrics.recordDeadLettered("importJob", "step1", 3);

            assertThat(meterRegistry.get(BatchMetrics.DEAD_LETTERED).tag("job", "importJob").tag("step", "step1")
                    .counter().count()).isEqualTo(5.0);
        }
    }

    // ----------------------------------------------------------------
    // Nothing duplicates Spring Batch's own meters
    // ----------------------------------------------------------------

    @Test
    @DisplayName("should not register the meters Spring Batch already publishes")
    void noDuplicatedMeters() {
        var execution = execution(null);
        batchMetrics.jobStarted(execution);
        batchMetrics.jobFinished(execution);

        assertThat(meterRegistry.getMeters()).extracting(meter -> meter.getId().getName())
                .containsOnly(BatchMetrics.RUN);
    }
}
