package com.adhar.kit.batch.partition;

import com.adhar.kit.batch.config.BatchAutoConfiguration;
import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.repository.BatchRepositoryLayout;
import com.adhar.kit.batch.runtime.AdharBatchRunner;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;

/**
 * Remote partitioning (design 0025 §5.10), when the application has Spring Batch Integration and
 * Spring Integration JDBC (both optional dependencies of the kit): the {@code jdbc} partition
 * transport on the JobRepository's database, the {@link RemotePartitioning} a job builds its manager
 * step with, and a worker's {@link PartitionWorker}, {@link StaleRequestGuard} and
 * {@link WorkerStopHandler}. Nothing here touches the database at startup.
 *
 * <p>Without those dependencies a worker pod exits 2 ({@code unsupported}) naming them.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@AutoConfiguration(after = BatchAutoConfiguration.class)
@ConditionalOnClass(name = {"org.springframework.batch.integration.partition.StepExecutionRequest",
        "org.springframework.integration.jdbc.store.JdbcChannelMessageStore"})
@ConditionalOnBooleanProperty(name = "adhar.batch.enabled", matchIfMissing = true)
@ConditionalOnBean({Job.class, JobRepository.class, BatchProperties.class})
public class RemotePartitioningAutoConfiguration {

    /**
     * @param dataSource      the application's DataSource
     * @param batchDataSource Spring Boot's dedicated batch DataSource, if any
     * @param layout          where the JobRepository's schema is
     * @param jobRepository   the JobRepository
     * @return the partition channel
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(DataSource.class)
    public JdbcPartitionTransport jdbcPartitionTransport(DataSource dataSource,
                                                         @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                                         ObjectProvider<BatchRepositoryLayout> layout,
                                                         JobRepository jobRepository) {
        return new JdbcPartitionTransport(batchDataSource.getIfAvailable(() -> dataSource), layout.getIfAvailable(),
                jobRepository);
    }

    /**
     * @param jobRepository the JobRepository
     * @param transport     the partition channel
     * @param properties    {@code adhar.batch.*}
     * @param environment   the role ({@code ADHAR_BATCH_ROLE}, {@code JOB_COMPLETION_INDEX})
     * @param beanFactory   the application's beans
     * @return what a job builds its manager step with
     */
    @Bean
    @ConditionalOnMissingBean
    public RemotePartitioning remotePartitioning(JobRepository jobRepository,
                                                 ObjectProvider<JdbcPartitionTransport> transport,
                                                 BatchProperties properties, Environment environment,
                                                 ListableBeanFactory beanFactory) {
        return new RemotePartitioning(jobRepository, transport, properties.getPartition(),
                OnBatchRoleCondition.effectiveRole(environment), beanFactory);
    }

    /**
     * @param jobRepository the JobRepository
     * @return the worker half of the stale-request guard
     */
    @Bean
    @ConditionalOnMissingBean
    public StaleRequestGuard staleRequestGuard(JobRepository jobRepository) {
        return new StaleRequestGuard(jobRepository);
    }

    /**
     * @return a worker's graceful stop
     */
    @Bean
    @ConditionalOnMissingBean
    public WorkerStopHandler workerStopHandler() {
        return new WorkerStopHandler();
    }

    /**
     * @param transport     the partition channel
     * @param guard         the stale-request guard
     * @param stop          the worker's graceful stop
     * @param jobRepository the JobRepository
     * @param beanFactory   the application's beans
     * @param properties    {@code adhar.batch.*}
     * @return what a worker pod runs instead of the job
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JdbcPartitionTransport.class)
    public PartitionWorker partitionWorker(JdbcPartitionTransport transport, StaleRequestGuard guard,
                                           WorkerStopHandler stop, JobRepository jobRepository,
                                           ListableBeanFactory beanFactory, BatchProperties properties) {
        return new PartitionWorker(transport, guard, stop, jobRepository, beanFactory,
                properties.getPartition().getPollInterval(), AdharBatchRunner.Sleeper.threadSleep());
    }
}
