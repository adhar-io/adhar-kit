package com.adhar.kit.batch;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.metrics.BatchJobStats;
import com.adhar.kit.batch.metrics.BatchMetrics;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BatchFacade}.
 */
@ExtendWith(MockitoExtension.class)
class BatchFacadeTest {

    @Mock
    private BatchScheduler scheduler;

    @Mock
    private BatchMetrics metrics;

    @BeforeEach
    void resetSingleton() throws Exception {
        Field instanceField = BatchFacade.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    // ----------------------------------------------------------------
    // Singleton
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("should return the same instance after construction")
        void returnsSameInstance() {
            BatchProperties props = new BatchProperties();
            new BatchFacade(scheduler, metrics, props);

            BatchFacade first = BatchFacade.getInstance();
            BatchFacade second = BatchFacade.getInstance();

            assertThat(first).isSameAs(second);
        }

        @Test
        @DisplayName("should throw when not yet initialised")
        void throwsWhenNotInitialised() {
            assertThatThrownBy(BatchFacade::getInstance)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not been initialised");
        }
    }

    // ----------------------------------------------------------------
    // isEnabled
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("isEnabled()")
    class IsEnabled {

        @Test
        @DisplayName("should default to true when properties are null")
        void defaultsTrueWithNullProps() {
            BatchFacade facade = new BatchFacade(null, null, null);

            assertThat(facade.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return properties value when properties are provided")
        void reflectsProperties() {
            BatchProperties props = new BatchProperties();
            props.setEnabled(false);
            BatchFacade facade = new BatchFacade(null, null, props);

            assertThat(facade.isEnabled()).isFalse();
        }
    }

    // ----------------------------------------------------------------
    // Default chunk / page sizes
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Default sizes")
    class DefaultSizes {

        @Test
        @DisplayName("getDefaultChunkSize returns 100 when properties are null")
        void chunkSizeNullProps() {
            BatchFacade facade = new BatchFacade(null, null, null);

            assertThat(facade.getDefaultChunkSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("getDefaultPageSize returns 50 when properties are null")
        void pageSizeNullProps() {
            BatchFacade facade = new BatchFacade(null, null, null);

            assertThat(facade.getDefaultPageSize()).isEqualTo(50);
        }

        @Test
        @DisplayName("getDefaultChunkSize reflects configured value")
        void chunkSizeFromProps() {
            BatchProperties props = new BatchProperties();
            props.setDefaultChunkSize(250);
            BatchFacade facade = new BatchFacade(null, null, props);

            assertThat(facade.getDefaultChunkSize()).isEqualTo(250);
        }

        @Test
        @DisplayName("getDefaultPageSize reflects configured value")
        void pageSizeFromProps() {
            BatchProperties props = new BatchProperties();
            props.setDefaultPageSize(75);
            BatchFacade facade = new BatchFacade(null, null, props);

            assertThat(facade.getDefaultPageSize()).isEqualTo(75);
        }
    }

    // ----------------------------------------------------------------
    // Health
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("health()")
    class Health {

        @Test
        @DisplayName("should return expected keys with scheduler and metrics")
        void healthKeys() {
            when(scheduler.listScheduledJobs()).thenReturn(Set.of("job1"));
            BatchFacade facade = new BatchFacade(scheduler, metrics, new BatchProperties());

            Map<String, Object> health = facade.health();

            assertThat(health).containsKeys(
                    "enabled",
                    "schedulerAvailable",
                    "metricsAvailable",
                    "scheduledJobCount",
                    "scheduledJobs"
            );
            assertThat(health.get("schedulerAvailable")).isEqualTo(true);
            assertThat(health.get("metricsAvailable")).isEqualTo(true);
            assertThat(health.get("scheduledJobCount")).isEqualTo(1);
        }

        @Test
        @DisplayName("should handle null scheduler and metrics gracefully")
        void healthNullComponents() {
            BatchFacade facade = new BatchFacade(null, null, null);

            Map<String, Object> health = facade.health();

            assertThat(health.get("schedulerAvailable")).isEqualTo(false);
            assertThat(health.get("metricsAvailable")).isEqualTo(false);
            assertThat(health.get("scheduledJobCount")).isEqualTo(0);
        }
    }

    // ----------------------------------------------------------------
    // Null scheduler/metrics (graceful handling)
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Graceful null handling")
    class GracefulNullHandling {

        @Test
        @DisplayName("scheduleJob throws when scheduler is null")
        void scheduleJobNullScheduler() {
            BatchFacade facade = new BatchFacade(null, null, null);

            assertThatThrownBy(() -> facade.scheduleJob("job", "0 0 * * * *"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("not available");
        }

        @Test
        @DisplayName("cancelJob throws when scheduler is null")
        void cancelJobNullScheduler() {
            BatchFacade facade = new BatchFacade(null, null, null);

            assertThatThrownBy(() -> facade.cancelJob("job"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("listScheduledJobs returns empty set when scheduler is null")
        void listJobsNullScheduler() {
            BatchFacade facade = new BatchFacade(null, null, null);

            assertThat(facade.listScheduledJobs()).isEmpty();
        }

        @Test
        @DisplayName("recordJobExecution does not throw when metrics is null")
        void recordJobNullMetrics() {
            BatchFacade facade = new BatchFacade(null, null, null);

            // Should not throw
            facade.recordJobExecution("job", 100L, true);
        }

        @Test
        @DisplayName("getJobStats returns zero stats when metrics is null")
        void getJobStatsNullMetrics() {
            BatchFacade facade = new BatchFacade(null, null, null);

            BatchJobStats stats = facade.getJobStats("job");

            assertThat(stats.totalExecutions()).isZero();
            assertThat(stats.successCount()).isZero();
            assertThat(stats.failureCount()).isZero();
            assertThat(stats.avgDurationMs()).isZero();
            assertThat(stats.lastExecutionTime()).isNull();
        }

        @Test
        @DisplayName("recordJobExecution delegates to metrics when available")
        void recordJobDelegatesToMetrics() {
            BatchFacade facade = new BatchFacade(null, metrics, null);

            facade.recordJobExecution("testJob", 500L, true);

            verify(metrics).recordJobExecution("testJob", 500L, true);
        }

        @Test
        @DisplayName("getJobStats delegates to metrics when available")
        void getJobStatsDelegatesToMetrics() {
            BatchJobStats expected = new BatchJobStats(5, 4, 1, 200.0, null);
            when(metrics.getJobStats("testJob")).thenReturn(expected);
            BatchFacade facade = new BatchFacade(null, metrics, null);

            BatchJobStats result = facade.getJobStats("testJob");

            assertThat(result).isEqualTo(expected);
        }
    }
}
