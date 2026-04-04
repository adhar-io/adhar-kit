package com.adhar.kit.batch.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link BatchMetrics}.
 */
class BatchMetricsTest {

    private MeterRegistry meterRegistry;
    private BatchMetrics batchMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        batchMetrics = new BatchMetrics(meterRegistry);
    }

    // ----------------------------------------------------------------
    // recordJobExecution
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("recordJobExecution()")
    class RecordJobExecution {

        @Test
        @DisplayName("should increment counters for successful execution")
        void incrementsSuccessCounter() {
            batchMetrics.recordJobExecution("importJob", 1000L, true);

            BatchJobStats stats = batchMetrics.getJobStats("importJob");

            assertThat(stats.totalExecutions()).isEqualTo(1);
            assertThat(stats.successCount()).isEqualTo(1);
            assertThat(stats.failureCount()).isZero();
        }

        @Test
        @DisplayName("should increment counters for failed execution")
        void incrementsFailureCounter() {
            batchMetrics.recordJobExecution("importJob", 500L, false);

            BatchJobStats stats = batchMetrics.getJobStats("importJob");

            assertThat(stats.totalExecutions()).isEqualTo(1);
            assertThat(stats.successCount()).isZero();
            assertThat(stats.failureCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should accumulate multiple executions")
        void accumulatesMultiple() {
            batchMetrics.recordJobExecution("importJob", 100L, true);
            batchMetrics.recordJobExecution("importJob", 200L, true);
            batchMetrics.recordJobExecution("importJob", 300L, false);

            BatchJobStats stats = batchMetrics.getJobStats("importJob");

            assertThat(stats.totalExecutions()).isEqualTo(3);
            assertThat(stats.successCount()).isEqualTo(2);
            assertThat(stats.failureCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("should register counters in the MeterRegistry")
        void registersInMeterRegistry() {
            batchMetrics.recordJobExecution("importJob", 100L, true);

            assertThat(meterRegistry.find("adhar.batch.job.executions")
                    .tag("job", "importJob")
                    .tag("status", "success")
                    .counter()).isNotNull();
        }

        @Test
        @DisplayName("should register timer in the MeterRegistry")
        void registersTimerInMeterRegistry() {
            batchMetrics.recordJobExecution("importJob", 100L, true);

            assertThat(meterRegistry.find("adhar.batch.job.duration")
                    .tag("job", "importJob")
                    .timer()).isNotNull();
        }
    }

    // ----------------------------------------------------------------
    // getJobStats
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getJobStats()")
    class GetJobStats {

        @Test
        @DisplayName("should return zero stats for unknown job")
        void zeroStatsForUnknown() {
            BatchJobStats stats = batchMetrics.getJobStats("unknownJob");

            assertThat(stats.totalExecutions()).isZero();
            assertThat(stats.successCount()).isZero();
            assertThat(stats.failureCount()).isZero();
            assertThat(stats.avgDurationMs()).isZero();
            assertThat(stats.lastExecutionTime()).isNull();
        }

        @Test
        @DisplayName("should compute correct average duration")
        void correctAvgDuration() {
            batchMetrics.recordJobExecution("job", 100L, true);
            batchMetrics.recordJobExecution("job", 300L, true);

            BatchJobStats stats = batchMetrics.getJobStats("job");

            assertThat(stats.avgDurationMs()).isEqualTo(200.0);
        }

        @Test
        @DisplayName("should set lastExecutionTime after recording")
        void setsLastExecutionTime() {
            batchMetrics.recordJobExecution("job", 100L, true);

            BatchJobStats stats = batchMetrics.getJobStats("job");

            assertThat(stats.lastExecutionTime()).isNotNull();
        }

        @Test
        @DisplayName("should track separate stats per job name")
        void separateStatsPerJob() {
            batchMetrics.recordJobExecution("jobA", 100L, true);
            batchMetrics.recordJobExecution("jobB", 200L, false);

            BatchJobStats statsA = batchMetrics.getJobStats("jobA");
            BatchJobStats statsB = batchMetrics.getJobStats("jobB");

            assertThat(statsA.totalExecutions()).isEqualTo(1);
            assertThat(statsA.successCount()).isEqualTo(1);
            assertThat(statsB.totalExecutions()).isEqualTo(1);
            assertThat(statsB.failureCount()).isEqualTo(1);
        }
    }

    // ----------------------------------------------------------------
    // recordStepExecution
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("recordStepExecution()")
    class RecordStepExecution {

        @Test
        @DisplayName("should register step-level counters in the MeterRegistry")
        void registersStepCounters() {
            batchMetrics.recordStepExecution("step1", 100, 95, 5);

            assertThat(meterRegistry.find("adhar.batch.step.reads")
                    .tag("step", "step1")
                    .counter()).isNotNull();
            assertThat(meterRegistry.find("adhar.batch.step.writes")
                    .tag("step", "step1")
                    .counter()).isNotNull();
            assertThat(meterRegistry.find("adhar.batch.step.skips")
                    .tag("step", "step1")
                    .counter()).isNotNull();
        }
    }
}
