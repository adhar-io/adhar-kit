package com.adhar.kit.batch.config;

import com.adhar.kit.batch.listener.AdharJobExecutionListener;
import com.adhar.kit.batch.metrics.BatchMetrics;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.launch.support.TaskExecutorJobLauncher;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

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
     * details including name, start/end time, duration, status, and exit code.
     *
     * @return the job execution listener
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharJobExecutionListener adharJobExecutionListener() {
        log.info("Registering AdharJobExecutionListener for batch job monitoring");
        return new AdharJobExecutionListener();
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
     * @return the batch scheduler
     */
    @Bean
    @ConditionalOnMissingBean
    public BatchScheduler batchScheduler(
            TaskScheduler taskScheduler,
            JobLauncher jobLauncher,
            ApplicationContext applicationContext) {
        log.info("Registering BatchScheduler for cron-based job scheduling");
        return new BatchScheduler(taskScheduler, jobLauncher, applicationContext);
    }
}
