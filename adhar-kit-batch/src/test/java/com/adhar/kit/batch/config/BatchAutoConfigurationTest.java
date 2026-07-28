package com.adhar.kit.batch.config;

import com.adhar.kit.batch.listener.AdharJobExecutionListener;
import com.adhar.kit.batch.listener.AdharSkipListener;
import com.adhar.kit.batch.listener.AdharStepExecutionListener;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.metrics.BatchMetrics;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.retry.RetryableStepBuilderFactory;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobLauncher;
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
 * Tests for {@link BatchAutoConfiguration} bean wiring using
 * {@link ApplicationContextRunner}.
 */
class BatchAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BatchAutoConfiguration.class))
            .withUserConfiguration(SupportConfig.class);

    @Test
    @DisplayName("registers the full set of batch beans when infrastructure is present")
    void registersAllBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AdharJobExecutionListener.class);
            assertThat(context).hasSingleBean(AdharStepExecutionListener.class);
            assertThat(context).hasSingleBean(AdharSkipListener.class);
            assertThat(context).hasSingleBean(RetryableStepBuilderFactory.class);
            assertThat(context).hasSingleBean(BatchOperator.class);
            assertThat(context).hasSingleBean(SchedulerLock.class);
            assertThat(context).hasSingleBean(BatchScheduler.class);
            assertThat(context).hasSingleBean(BatchMetrics.class);
        });
    }

    @Test
    @DisplayName("retry factory reflects configured properties")
    void retryFactoryUsesProperties() {
        runner.withPropertyValues("adhar.batch.max-retries=7", "adhar.batch.retry-on-failure=false")
                .run(context -> {
                    var factory = context.getBean(RetryableStepBuilderFactory.class);
                    assertThat(factory.getDefaultRetryLimit()).isEqualTo(7);
                    assertThat(factory.isRetryEnabled()).isFalse();
                });
    }

    @Test
    @DisplayName("BatchOperator is absent without a JobOperator bean")
    void noBatchOperatorWithoutJobOperator() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(BatchAutoConfiguration.class))
                .withUserConfiguration(NoOperatorConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(BatchOperator.class));
    }

    @Test
    @DisplayName("module can be disabled via property")
    void disabledViaProperty() {
        runner.withPropertyValues("adhar.batch.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(BatchScheduler.class));
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

        @Bean
        JobLauncher jobLauncher() {
            return mock(JobLauncher.class);
        }
    }
}
