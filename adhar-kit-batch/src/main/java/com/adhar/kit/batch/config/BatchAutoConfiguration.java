package com.adhar.kit.batch.config;

import com.adhar.kit.batch.listener.AdharJobExecutionListener;
import com.adhar.kit.batch.listener.AdharSkipListener;
import com.adhar.kit.batch.listener.AdharStepExecutionListener;
import com.adhar.kit.batch.lock.JdbcSchedulerLock;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.metrics.BatchMetrics;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.retry.RetryableStepBuilderFactory;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Auto-configuration for Adhar Batch module.
 *
 * <p>Configures Spring Batch with a custom {@link AdharJobExecutionListener} for
 * comprehensive job lifecycle logging and a {@link TaskExecutorJobLauncher} for
 * asynchronous job execution.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Custom job execution listener with timing and status logging</li>
 *   <li>Async job launcher with configurable concurrency</li>
 *   <li>Configurable table prefix for batch metadata</li>
 *   <li>Retry support for failed jobs</li>
 * </ul>
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   batch:
 *     enabled: true
 *     table-prefix: BATCH_
 *     max-concurrent-jobs: 5
 *     retry-on-failure: true
 *     max-retries: 3
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(Job.class)
@ConditionalOnProperty(prefix = "adhar.batch", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(BatchProperties.class)
public class BatchAutoConfiguration {

    @PostConstruct
    public void logBatchConfiguration() {
        log.info("Adhar Batch module initialized with Spring Batch support (async job launcher, execution listener)");
    }

    /**
     * Registers a custom {@link AdharJobExecutionListener} that logs job execution
     * details, records execution metrics (when a {@link BatchMetrics} bean is
     * available), and publishes a
     * {@link com.adhar.kit.batch.event.BatchJobFailedEvent} on failure.
     *
     * @param batchMetrics   the optional metrics collector
     * @param eventPublisher the application event publisher
     * @return the job execution listener
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharJobExecutionListener adharJobExecutionListener(
            ObjectProvider<BatchMetrics> batchMetrics,
            ApplicationEventPublisher eventPublisher) {
        log.info("Registering AdharJobExecutionListener for batch job monitoring");
        return new AdharJobExecutionListener(batchMetrics.getIfAvailable(), eventPublisher);
    }

    /**
     * Registers an {@link AdharStepExecutionListener} that logs step execution
     * and records per-step read/write/skip counts when metrics are available.
     *
     * @param batchMetrics the optional metrics collector
     * @return the step execution listener
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharStepExecutionListener adharStepExecutionListener(ObjectProvider<BatchMetrics> batchMetrics) {
        log.info("Registering AdharStepExecutionListener for batch step monitoring");
        return new AdharStepExecutionListener(batchMetrics.getIfAvailable());
    }

    /**
     * Registers an {@link AdharSkipListener} that logs skipped items and records
     * skip metrics when metrics are available.
     *
     * @param batchMetrics the optional metrics collector
     * @return the skip listener
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharSkipListener<Object, Object> adharSkipListener(ObjectProvider<BatchMetrics> batchMetrics) {
        log.info("Registering AdharSkipListener for batch skip monitoring");
        return new AdharSkipListener<>(batchMetrics.getIfAvailable());
    }

    /**
     * Registers a {@link RetryableStepBuilderFactory} that seeds
     * {@link com.adhar.kit.batch.retry.RetryableStepBuilder} instances with the
     * configured default retry policy ({@code adhar.batch.max-retries} and
     * {@code adhar.batch.retry-on-failure}). Explicit
     * {@code withRetryLimit(...)} calls still override these defaults.
     *
     * @param batchProperties the batch configuration properties
     * @return the retryable step builder factory
     */
    @Bean
    @ConditionalOnMissingBean
    public RetryableStepBuilderFactory retryableStepBuilderFactory(BatchProperties batchProperties) {
        log.info("Registering RetryableStepBuilderFactory (maxRetries={}, retryOnFailure={})",
                batchProperties.getMaxRetries(), batchProperties.isRetryOnFailure());
        return new RetryableStepBuilderFactory(batchProperties.getMaxRetries(), batchProperties.isRetryOnFailure());
    }

    /**
     * Registers a {@link BatchOperator} for restart/stop/abandon operations on
     * job executions. Only activated when a {@link JobOperator} bean is present.
     *
     * @param jobOperator   the Spring Batch job operator
     * @param jobRepository the Spring Batch job repository
     * @return the batch operator
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JobOperator.class)
    public BatchOperator batchOperator(JobOperator jobOperator, JobRepository jobRepository) {
        log.info("Registering BatchOperator for job execution management");
        return new BatchOperator(jobOperator, jobRepository);
    }

    /**
     * Registers a JDBC-backed {@link SchedulerLock} for multi-instance cron
     * safety. Only activated when a {@link DataSource} is present; applications
     * may supply their own {@link SchedulerLock} bean to override.
     *
     * @param dataSource the data source backing the lock table
     * @return the scheduler lock
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public SchedulerLock schedulerLock(DataSource dataSource) {
        log.info("Registering JdbcSchedulerLock for multi-instance cron safety");
        return new JdbcSchedulerLock(dataSource);
    }

    /**
     * Registers a {@link TaskExecutorJobLauncher} for asynchronous job execution.
     * The concurrency level is controlled by {@code adhar.batch.max-concurrent-jobs}.
     *
     * @param jobRepository    the job repository for persisting job metadata
     * @param batchProperties  the batch configuration properties
     * @return the async job launcher
     * @throws Exception if the job launcher cannot be initialized
     */
    @Bean
    @ConditionalOnMissingBean(JobLauncher.class)
    public TaskExecutorJobLauncher adharJobLauncher(
            JobRepository jobRepository,
            BatchProperties batchProperties) throws Exception {
        var jobLauncher = new TaskExecutorJobLauncher();
        jobLauncher.setJobRepository(jobRepository);

        var taskExecutor = new SimpleAsyncTaskExecutor("adhar-batch-");
        taskExecutor.setConcurrencyLimit(batchProperties.getMaxConcurrentJobs());
        jobLauncher.setTaskExecutor(taskExecutor);

        jobLauncher.afterPropertiesSet();

        log.info("Configured async JobLauncher with concurrency limit: {}", batchProperties.getMaxConcurrentJobs());
        return jobLauncher;
    }

    /**
     * Registers a {@link BatchMetrics} bean for collecting batch job and step metrics
     * via Micrometer. Only activated when a {@link MeterRegistry} is available.
     *
     * @param meterRegistry the Micrometer meter registry
     * @return the batch metrics collector
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnClass(MeterRegistry.class)
    public BatchMetrics batchMetrics(MeterRegistry meterRegistry) {
        log.info("Registering BatchMetrics with Micrometer MeterRegistry");
        return new BatchMetrics(meterRegistry);
    }

    /**
     * Registers a default {@link TaskScheduler} for batch job scheduling
     * if none is already present in the application context.
     *
     * @return the task scheduler
     */
    @Bean
    @ConditionalOnMissingBean(TaskScheduler.class)
    public TaskScheduler batchTaskScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("adhar-batch-scheduler-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * Registers a {@link BatchScheduler} bean for scheduling batch jobs
     * at runtime using cron expressions.
     *
     * @param taskScheduler      the task scheduler
     * @param jobLauncher        the job launcher
     * @param applicationContext the Spring application context for job bean lookup
     * @param schedulerLock      the optional distributed scheduler lock for multi-instance safety
     * @return the batch scheduler
     */
    @Bean
    @ConditionalOnMissingBean
    public BatchScheduler batchScheduler(
            TaskScheduler taskScheduler,
            JobLauncher jobLauncher,
            ApplicationContext applicationContext,
            ObjectProvider<SchedulerLock> schedulerLock) {
        var lock = schedulerLock.getIfAvailable();
        log.info("Registering BatchScheduler for cron-based job scheduling (distributedLock={})", lock != null);
        return new BatchScheduler(taskScheduler, jobLauncher, applicationContext, lock, Duration.ofMinutes(30));
    }
}
