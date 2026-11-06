package com.adhar.kit.batch;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.metrics.BatchJobStats;
import com.adhar.kit.batch.operator.BatchOperator;
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
    private BatchOperator operator;

    @BeforeEach
    void resetSingleton() throws Exception {
        Field instanceField = BatchFacade.class.getDeclaredField("instance");
        instanceField.setAccessible(true);
        instanceField.set(null, null);
    }

    private BatchFacade defaults() {
        return new BatchFacade(null, null, null);
    }

    // ----------------------------------------------------------------
    // Singleton
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("getInstance()")
    class GetInstance {

        @Test
        @DisplayName("should return a facade with the defaults when no context published one")
        void defaultsWithoutAContext() {
            BatchFacade first = BatchFacade.getInstance();

            assertThat(first).isSameAs(BatchFacade.getInstance());
            assertThat(first.getDefaultChunkSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("should return the published facade until it is withdrawn")
        void publishedUntilWithdrawn() {
            BatchFacade published = defaults();

            BatchFacade.publish(published);
            assertThat(BatchFacade.getInstance()).isSameAs(published);

            BatchFacade.withdraw(published);
            assertThat(BatchFacade.getInstance()).isNotSameAs(published);
        }

        @Test
        @DisplayName("withdrawing a facade another context replaced changes nothing")
        void withdrawIgnoresAReplacedFacade() {
            BatchFacade replaced = defaults();
            BatchFacade current = defaults();
            BatchFacade.publish(replaced);
            BatchFacade.publish(current);

            BatchFacade.withdraw(replaced);

            assertThat(BatchFacade.getInstance()).isSameAs(current);
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
            assertThat(defaults().isEnabled()).isTrue();
        }

        @Test
        @DisplayName("should return properties value when properties are provided")
        void reflectsProperties() {
            BatchProperties props = new BatchProperties();
            props.setEnabled(false);

            assertThat(new BatchFacade(props, null, null).isEnabled()).isFalse();
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
            assertThat(defaults().getDefaultChunkSize()).isEqualTo(100);
        }

        @Test
        @DisplayName("getDefaultPageSize returns 500 when properties are null")
        void pageSizeNullProps() {
            assertThat(defaults().getDefaultPageSize()).isEqualTo(500);
        }

        @Test
        @DisplayName("getDefaultChunkSize reflects configured value")
        void chunkSizeFromProps() {
            BatchProperties props = new BatchProperties();
            props.getChunk().setSize(250);

            assertThat(new BatchFacade(props, null, null).getDefaultChunkSize()).isEqualTo(250);
        }

        @Test
        @DisplayName("getDefaultPageSize reflects configured value")
        void pageSizeFromProps() {
            BatchProperties props = new BatchProperties();
            props.getPage().setSize(75);

            assertThat(new BatchFacade(props, null, null).getDefaultPageSize()).isEqualTo(75);
        }
    }

    // ----------------------------------------------------------------
    // Health
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("health()")
    class Health {

        @Test
        @DisplayName("should describe the scheduler and the operator when available")
        void healthKeys() {
            when(scheduler.listScheduledJobs()).thenReturn(Set.of("job1"));
            when(scheduler.runningJobs()).thenReturn(Set.of("job1"));
            BatchFacade facade = new BatchFacade(new BatchProperties(), () -> scheduler, () -> operator);

            Map<String, Object> health = facade.health();

            assertThat(health.get("enabled")).isEqualTo(true);
            assertThat(health.get("operatorAvailable")).isEqualTo(true);
            assertThat(health.get("schedulerAvailable")).isEqualTo(true);
            assertThat(health.get("scheduledJobCount")).isEqualTo(1);
            assertThat(health.get("scheduledJobs")).isEqualTo(Set.of("job1"));
            assertThat(health.get("runningScheduledJobs")).isEqualTo(Set.of("job1"));
        }

        @Test
        @DisplayName("should handle a missing scheduler and operator gracefully")
        void healthNullComponents() {
            Map<String, Object> health = defaults().health();

            assertThat(health.get("operatorAvailable")).isEqualTo(false);
            assertThat(health.get("schedulerAvailable")).isEqualTo(false);
            assertThat(health.get("scheduledJobCount")).isEqualTo(0);
        }
    }

    // ----------------------------------------------------------------
    // Without a scheduler / operator
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Graceful null handling")
    class GracefulNullHandling {

        @Test
        @DisplayName("scheduleJob explains that the scheduler is not enabled")
        void scheduleJobNullScheduler() {
            assertThatThrownBy(() -> defaults().scheduleJob("job", "0 0 * * * *"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("adhar.batch.scheduler.enabled=true");
        }

        @Test
        @DisplayName("cancelJob throws when the scheduler is not enabled")
        void cancelJobNullScheduler() {
            assertThatThrownBy(() -> defaults().cancelJob("job"))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("listScheduledJobs returns an empty set when the scheduler is not enabled")
        void listJobsNullScheduler() {
            assertThat(defaults().listScheduledJobs()).isEmpty();
            assertThat(defaults().getScheduler()).isEmpty();
        }

        @Test
        @DisplayName("getOperator and getJobStats explain what is missing")
        void noOperator() {
            assertThatThrownBy(() -> defaults().getOperator())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No BatchOperator");
            assertThatThrownBy(() -> defaults().getJobStats("job"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    // ----------------------------------------------------------------
    // Delegation
    // ----------------------------------------------------------------

    @Nested
    @DisplayName("Delegation")
    class Delegation {

        @Test
        @DisplayName("scheduling goes to the scheduler")
        void schedulingDelegates() {
            when(scheduler.cancelScheduledJob("job")).thenReturn(true);
            when(scheduler.listScheduledJobs()).thenReturn(Set.of("job"));
            BatchFacade facade = new BatchFacade(null, () -> scheduler, null);

            facade.scheduleJob("job", "0 0 2 * * *");

            verify(scheduler).scheduleJob("job", "0 0 2 * * *");
            assertThat(facade.cancelJob("job")).isTrue();
            assertThat(facade.listScheduledJobs()).containsExactly("job");
            assertThat(facade.getScheduler()).containsSame(scheduler);
        }

        @Test
        @DisplayName("getJobStats comes from the operator")
        void getJobStatsDelegatesToOperator() {
            BatchJobStats expected = new BatchJobStats(5, 4, 1, 200.0, null);
            when(operator.getJobStats("testJob")).thenReturn(expected);
            BatchFacade facade = new BatchFacade(null, null, () -> operator);

            assertThat(facade.getJobStats("testJob")).isEqualTo(expected);
            assertThat(facade.getOperator()).isSameAs(operator);
        }
    }
}
