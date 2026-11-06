package com.adhar.kit.batch.config;

import com.adhar.kit.batch.listener.AdharChunkListener;
import com.adhar.kit.batch.listener.AdharJobExecutionListener;
import com.adhar.kit.batch.listener.AdharSkipListener;
import com.adhar.kit.batch.listener.AdharStepExecutionListener;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.metrics.BatchMetrics;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.step.AdharSteps;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for the conditions of {@link BatchAutoConfiguration} using
 * {@link ApplicationContextRunner}. The wiring against Spring Boot's real auto-configurations is
 * {@link BatchAutoConfigurationTestContainerTest}.
 */
class BatchAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BatchAutoConfiguration.class));

    private final ApplicationContextRunner withJob = runner
            .withUserConfiguration(SupportConfig.class, JobConfig.class);

    @Test
    @DisplayName("registers the full set of batch beans when the application defines a job")
    void registersAllBeans() {
        withJob.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AdharJobExecutionListener.class);
            assertThat(context).hasSingleBean(AdharStepExecutionListener.class);
            assertThat(context).hasSingleBean(AdharChunkListener.class);
            assertThat(context).hasSingleBean(AdharSkipListener.class);
            assertThat(context).hasSingleBean(AdharSteps.class);
            assertThat(context).hasSingleBean(BatchOperator.class);
            assertThat(context).hasSingleBean(SchedulerLock.class);
            assertThat(context).hasSingleBean(BatchMetrics.class);
        });
    }

    @Test
    @DisplayName("a service without jobs gets none of the module's beans")
    void noBeansWithoutAJob() {
        runner.withUserConfiguration(SupportConfig.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(BatchProperties.class);
            assertThat(context).doesNotHaveBean(AdharJobExecutionListener.class);
            assertThat(context).doesNotHaveBean(BatchOperator.class);
            assertThat(context).doesNotHaveBean(SchedulerLock.class);
        });
    }

    @Test
    @DisplayName("retry settings bind from adhar.batch.retry")
    void retrySettingsBind() {
        withJob.withPropertyValues("adhar.batch.retry.max-attempts=7")
                .run(context -> assertThat(context.getBean(BatchProperties.class).getRetry().getMaxAttempts())
                        .isEqualTo(7));
    }

    @Test
    @DisplayName("an invalid setting fails the startup naming the property")
    void invalidSettingFailsTheStartup() {
        withJob.withPropertyValues("adhar.batch.retry.max-attempts=0")
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("adhar.batch.retry.max-attempts must be at least 1"));
    }

    @Test
    @DisplayName("BatchOperator is absent without a JobOperator bean")
    void noBatchOperatorWithoutJobOperator() {
        runner.withUserConfiguration(NoOperatorConfig.class, JobConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(BatchOperator.class);
                });
    }

    @Test
    @DisplayName("the embedded scheduler is opt-in")
    void schedulerIsOptIn() {
        withJob.run(context -> assertThat(context).doesNotHaveBean(BatchScheduler.class));
        // lock=none: the H2 database here has no lock table, and a started scheduler verifies its lock.
        withJob.withPropertyValues("adhar.batch.scheduler.enabled=true", "adhar.batch.scheduler.lock=none")
                .run(context -> assertThat(context).hasSingleBean(BatchScheduler.class));
    }

    @Test
    @DisplayName("an enabled scheduler fails the startup when its lock table is missing")
    void schedulerFailsTheStartupWithoutItsLockTable() {
        withJob.withPropertyValues("adhar.batch.scheduler.enabled=true")
                .run(context -> assertThat(context).getFailure()
                        .hasStackTraceContaining("The scheduler lock table"));
    }

    @Test
    @DisplayName("module can be disabled via property")
    void disabledViaProperty() {
        withJob.withPropertyValues("adhar.batch.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(BatchProperties.class);
                    assertThat(context).doesNotHaveBean(BatchOperator.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class JobConfig {
        @Bean
        Job job() {
            return mock(Job.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SupportConfig {
        @Bean
        JobRepository jobRepository() {
            return mock(JobRepository.class);
        }

        @Bean
        JobOperator jobOperator() {
            return mock(JobOperator.class);
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder()
                    .setType(EmbeddedDatabaseType.H2)
                    .setName("autoconfig-" + System.nanoTime())
                    .build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NoOperatorConfig {
        @Bean
        JobRepository jobRepository() {
            return mock(JobRepository.class);
        }
    }
}
