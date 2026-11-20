package com.adhar.kit.batch.config;

import com.adhar.adharkit.logging.batch.BatchJobLogger;
import com.adhar.kit.batch.BatchFacadeRegistrar;
import com.adhar.kit.batch.event.BatchRunEventPublisher;
import com.adhar.kit.batch.fault.DeadLetterSink;
import com.adhar.kit.batch.fault.DeadLetterSkipListener;
import com.adhar.kit.batch.fault.JdbcDeadLetterSink;
import com.adhar.kit.batch.listener.AdharChunkListener;
import com.adhar.kit.batch.listener.AdharJobExecutionListener;
import com.adhar.kit.batch.listener.AdharSkipListener;
import com.adhar.kit.batch.listener.AdharStepExecutionListener;
import com.adhar.kit.batch.listener.BatchJobLoggerObserver;
import com.adhar.kit.batch.listener.BatchListenerAttacher;
import com.adhar.kit.batch.listener.BatchRunObserver;
import com.adhar.kit.batch.lock.JdbcSchedulerLock;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.telemetry.BatchProgressMetrics;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.partition.WorkerStopHandler;
import com.adhar.kit.batch.operator.JobResolver;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.repository.AdharBatchRepositoryConfiguration;
import com.adhar.kit.batch.repository.BatchRepositoryLayout;
import com.adhar.kit.batch.repository.ResourcelessRepositoryGuard;
import com.adhar.kit.batch.runtime.AdharBatchRunner;
import com.adhar.kit.batch.runtime.BatchExitCodeGenerator;
import com.adhar.kit.batch.runtime.ExecutionLease;
import com.adhar.kit.batch.runtime.GracefulStopLifecycle;
import com.adhar.kit.batch.runtime.JdbcExecutionLease;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup;
import com.adhar.kit.batch.runtime.PartitionConsumer;
import com.adhar.kit.batch.runtime.RecoveryPolicy;
import com.adhar.kit.batch.runtime.RunContextResolver;
import com.adhar.kit.batch.runtime.TerminationSummaryWriter;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.MicrometerSchedulerMetrics;
import com.adhar.kit.batch.scheduler.SchedulerMetrics;
import com.adhar.kit.batch.scheduler.SimpleSchedulerEngine;
import com.adhar.kit.batch.scheduler.quartz.QuartzConfiguration;
import com.adhar.kit.batch.step.AdharSteps;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.batch.autoconfigure.BatchTransactionManager;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchDataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.ClassUtils;

import javax.sql.DataSource;
import java.time.Clock;

/**
 * Auto-configuration of the Adhar Batch module.
 *
 * <p>Ordered after Spring Boot's DataSource, transaction, batch, metrics and observation
 * auto-configurations, so its conditions see their beans (K2: the first release declared no
 * order and evaluated {@code @ConditionalOnBean(DataSource|MeterRegistry|JobOperator)} before
 * those beans existed). Active only when the application defines at least one {@link Job}: a
 * service without jobs gets none of the module's beans, whatever its classpath.</p>
 *
 * <p>It provides the JDBC repository management ({@link AdharBatchRepositoryConfiguration}), the
 * refusal of an in-memory repository, {@link AdharSteps}, the kit's listeners attached to every
 * job and step, {@link BatchOperator}, a {@link SchedulerLock}, the
 * {@link com.adhar.kit.batch.BatchFacade} and, when {@code adhar.batch.scheduler.enabled}, the
 * embedded {@link BatchScheduler} on its engine {@code simple} or, with
 * {@code adhar.batch.scheduler.engine=quartz}, {@code quartz} ({@link QuartzConfiguration}). Metrics, the log context and the trace filter are
 * {@link com.adhar.kit.batch.telemetry.BatchTelemetryAutoConfiguration}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
@AutoConfiguration(afterName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration",
        "org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcAutoConfiguration",
        "org.springframework.boot.batch.autoconfigure.BatchAutoConfiguration",
        "org.springframework.boot.batch.autoconfigure.BatchJobLauncherAutoConfiguration",
        "org.springframework.boot.batch.autoconfigure.observation.BatchObservationAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration",
        "org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration",
        "com.adhar.adharkit.logging.config.AdharLoggingAutoConfiguration"})
@ConditionalOnClass({Job.class, JobOperator.class})
@ConditionalOnBooleanProperty(name = "adhar.batch.enabled", matchIfMissing = true)
@ConditionalOnBean(Job.class)
@EnableConfigurationProperties(BatchProperties.class)
@Import({AdharBatchRepositoryConfiguration.class, QuartzConfiguration.class})
public class BatchAutoConfiguration {

    /**
     * @param properties {@code adhar.batch.*}, validated here so a misconfiguration fails the
     *                   startup with the property's name
     */
    public BatchAutoConfiguration(BatchProperties properties) {
        properties.validate();
    }

    /**
     * @param environment the application's environment
     * @return the registrar publishing the {@link com.adhar.kit.batch.BatchFacade} before any
     *         application bean is created
     */
    @Bean
    public static BatchFacadeRegistrar batchFacadeRegistrar(Environment environment) {
        return new BatchFacadeRegistrar(environment);
    }

    /**
     * @param jobListener   the kit's job listener
     * @param stepListener  the kit's step listener
     * @param chunkListener the kit's chunk listener
     * @param skipListener  the kit's skip listener
     * @return the post-processor attaching them to every job and step
     */
    @Bean
    public static BatchListenerAttacher batchListenerAttacher(ObjectProvider<AdharJobExecutionListener> jobListener,
                                                              ObjectProvider<AdharStepExecutionListener> stepListener,
                                                              ObjectProvider<AdharChunkListener> chunkListener,
                                                              ObjectProvider<AdharSkipListener> skipListener) {
        return new BatchListenerAttacher(jobListener, stepListener, chunkListener, skipListener);
    }

    /**
     * @param eventPublisher publishes {@link com.adhar.kit.batch.event.BatchJobFailedEvent}
     * @param observers      the run observers
     * @param specs          the declared parameters, for the sensitive names
     * @return the job listener
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharJobExecutionListener adharJobExecutionListener(ApplicationEventPublisher eventPublisher,
                                                               ObjectProvider<BatchRunObserver> observers,
                                                               ObjectProvider<JobParameterSpecRegistry> specs) {
        return new AdharJobExecutionListener(eventPublisher, observers.orderedStream().toList(),
                specs.getIfAvailable());
    }

    /**
     * @param observers the run observers
     * @return the step listener
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharStepExecutionListener adharStepExecutionListener(ObjectProvider<BatchRunObserver> observers) {
        return new AdharStepExecutionListener(observers.orderedStream().toList());
    }

    /**
     * @param observers the run observers
     * @return the chunk listener
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharChunkListener adharChunkListener(ObjectProvider<BatchRunObserver> observers) {
        return new AdharChunkListener(observers.orderedStream().toList());
    }

    /**
     * @param observers the run observers
     * @return the skip listener
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharSkipListener adharSkipListener(ObjectProvider<BatchRunObserver> observers) {
        return new AdharSkipListener(observers.orderedStream().toList());
    }

    /**
     * @param jobRepository the JobRepository
     * @param properties    {@code adhar.batch.*}
     * @return the guard refusing an in-memory JobRepository
     */
    @Bean
    public ResourcelessRepositoryGuard resourcelessRepositoryGuard(ObjectProvider<JobRepository> jobRepository,
                                                                   BatchProperties properties) {
        return new ResourcelessRepositoryGuard(jobRepository, properties.getRepository().isAllowInMemory());
    }

    /**
     * @param beanFactory the application's beans
     * @return resolves jobs by bean name or job name
     */
    @Bean
    @ConditionalOnMissingBean
    public JobResolver batchJobResolver(ListableBeanFactory beanFactory) {
        return JobResolver.of(beanFactory);
    }

    /**
     * @param publisher the application's event publisher
     * @param specs     the declared parameters, for the sensitive names
     * @return publishes {@link com.adhar.kit.batch.event.BatchRunEvent}s for every run
     */
    @Bean
    @ConditionalOnMissingBean
    public BatchRunEventPublisher batchRunEventPublisher(ApplicationEventPublisher publisher,
                                                         ObjectProvider<JobParameterSpecRegistry> specs) {
        return new BatchRunEventPublisher(publisher, specs);
    }

    /**
     * @param beanFactory the application's beans
     * @return the declared parameters of every job
     */
    @Bean
    @ConditionalOnMissingBean
    public JobParameterSpecRegistry jobParameterSpecRegistry(ListableBeanFactory beanFactory) {
        return new JobParameterSpecRegistry(beanFactory);
    }

    /**
     * @return validates a run's input against its job's declaration
     */
    @Bean
    @ConditionalOnMissingBean
    public JobParametersResolver jobParametersResolver() {
        return new JobParametersResolver();
    }

    /**
     * @param jobOperator   Spring Batch's operator
     * @param jobRepository Spring Batch's repository
     * @param jobResolver   resolves jobs by name
     * @param specs         the declared parameters, for the sensitive names
     * @return the kit's operator
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({JobOperator.class, JobRepository.class})
    public BatchOperator batchOperator(JobOperator jobOperator, JobRepository jobRepository, JobResolver jobResolver,
                                       ObjectProvider<JobParameterSpecRegistry> specs) {
        return new BatchOperator(jobOperator, jobRepository, jobResolver, specs.getIfAvailable());
    }

    /**
     * @param jobRepository           the JobRepository
     * @param batchTransactionManager Spring Boot's dedicated batch transaction manager, if any
     * @param transactionManager      the application's transaction manager
     * @param listeners               attaches the kit's listeners
     * @param properties              {@code adhar.batch.*}
     * @param observationRegistry     the application's observation registry, if any
     * @param deadLetters             keeps skipped items, when there is a dead-letter table
     * @return the step builder entry point
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(JobRepository.class)
    public AdharSteps adharSteps(JobRepository jobRepository,
                                 @BatchTransactionManager ObjectProvider<PlatformTransactionManager> batchTransactionManager,
                                 ObjectProvider<PlatformTransactionManager> transactionManager,
                                 BatchListenerAttacher listeners, BatchProperties properties,
                                 ObjectProvider<ObservationRegistry> observationRegistry,
                                 ObjectProvider<DeadLetterSkipListener> deadLetters) {
        // The JobRepository's transaction manager, chosen as Spring Boot chooses it, so business
        // writes and step progress commit together (INV-4).
        PlatformTransactionManager manager = batchTransactionManager.getIfAvailable(
                () -> transactionManager.getIfAvailable(ResourcelessTransactionManager::new));
        return new AdharSteps(jobRepository, manager, listeners, properties, observationRegistry.getIfAvailable(),
                deadLetters.getIfAvailable());
    }

    /**
     * Ties the logging module's structured BATCH events to every run, when the logging module is
     * present.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.adhar.adharkit.logging.batch.BatchJobLogger")
    static class BatchJobLoggingConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(BatchJobLogger.class)
        BatchJobLoggerObserver batchJobLoggerObserver(BatchJobLogger batchJobLogger) {
            return new BatchJobLoggerObserver(batchJobLogger);
        }
    }

    /**
     * The lock of the embedded scheduler, on the JobRepository's database.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(name = "adhar.batch.scheduler.lock", havingValue = "jdbc", matchIfMissing = true)
    static class SchedulerLockConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SchedulerLock schedulerLock(DataSource dataSource, @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                    ObjectProvider<BatchRepositoryLayout> layout) {
            BatchRepositoryLayout resolved = layout.getIfAvailable();
            String table = resolved != null ? resolved.kitTable(BatchRepositoryLayout.SCHEDULER_LOCK_TABLE)
                    : JdbcSchedulerLock.DEFAULT_TABLE;
            return new JdbcSchedulerLock(batchDataSource.getIfAvailable(() -> dataSource), table);
        }
    }

    /**
     * The dead-letter table of fault-tolerant steps, on the JobRepository's database.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnBooleanProperty(name = "adhar.batch.dead-letter.enabled", matchIfMissing = true)
    static class DeadLetterConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DeadLetterSink deadLetterSink(DataSource dataSource, @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                      ObjectProvider<BatchRepositoryLayout> layout) {
            BatchRepositoryLayout resolved = layout.getIfAvailable();
            String table = resolved != null ? resolved.kitTable(BatchRepositoryLayout.DEAD_LETTER_TABLE)
                    : BatchRepositoryLayout.DEAD_LETTER_TABLE;
            return new JdbcDeadLetterSink(new JdbcTemplate(batchDataSource.getIfAvailable(() -> dataSource)), table,
                    resolved != null && resolved.managed());
        }

        @Bean
        @ConditionalOnMissingBean
        DeadLetterSkipListener deadLetterSkipListener(DeadLetterSink sink, ObjectProvider<BatchProgressMetrics> metrics) {
            return new DeadLetterSkipListener(sink, DeadLetterSkipListener.defaultSerializer(), metrics.getIfAvailable());
        }
    }

    /**
     * Kubernetes mode ({@code adhar.batch.mode=kubernetes}, resolved by
     * {@link BatchRuntimeEnvironmentPostProcessor}): the runner of one job per pod, its lease,
     * recovery, graceful stop, termination summary and exit code (design 0025 §5.5).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = BatchRuntimeEnvironmentPostProcessor.MODE_PROPERTY, havingValue = "kubernetes")
    static class KubernetesRunConfiguration {

        @Bean
        @ConditionalOnMissingBean
        KubernetesOwnerLookup kubernetesOwnerLookup() {
            return KubernetesOwnerLookup.inCluster(System::getenv, KubernetesOwnerLookup.SERVICE_ACCOUNT);
        }

        @Bean
        @ConditionalOnMissingBean
        RunContextResolver runContextResolver(Environment environment, KubernetesOwnerLookup lookup,
                                              BatchProperties properties) {
            return new RunContextResolver(environment::getProperty, lookup, properties);
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(DataSource.class)
        ExecutionLease executionLease(DataSource dataSource, @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                      ObjectProvider<BatchRepositoryLayout> layout) {
            BatchRepositoryLayout resolved = layout.getIfAvailable();
            String table = resolved != null ? resolved.kitTable(BatchRepositoryLayout.EXECUTION_LEASE_TABLE)
                    : BatchRepositoryLayout.EXECUTION_LEASE_TABLE;
            return new JdbcExecutionLease(new JdbcTemplate(batchDataSource.getIfAvailable(() -> dataSource)), table);
        }

        @Bean
        @ConditionalOnMissingBean
        GracefulStopLifecycle gracefulStopLifecycle(ObjectProvider<JobOperator> jobOperator,
                                                    ObjectProvider<WorkerStopHandler> workerStop,
                                                    BatchProperties properties) {
            return new GracefulStopLifecycle(jobOperator, properties.getShutdown().getTimeout(),
                    workerStop.getIfAvailable());
        }

        @Bean
        @ConditionalOnMissingBean
        TerminationSummaryWriter terminationSummaryWriter() {
            return new TerminationSummaryWriter(TerminationSummaryWriter.TERMINATION_LOG, System.out);
        }

        @Bean
        @ConditionalOnMissingBean
        BatchExitCodeGenerator batchExitCodeGenerator() {
            return new BatchExitCodeGenerator();
        }

        @Bean
        @ConditionalOnMissingBean
        AdharBatchRunner adharBatchRunner(ObjectProvider<JobRepository> jobRepository,
                                          ObjectProvider<JobOperator> jobOperator, ObjectProvider<JobResolver> jobResolver,
                                          ObjectProvider<JobParameterSpecRegistry> specs,
                                          ObjectProvider<JobParametersResolver> parametersResolver,
                                          ObjectProvider<RunContextResolver> contextResolver,
                                          ObjectProvider<KubernetesOwnerLookup> lookup,
                                          ObjectProvider<ExecutionLease> lease,
                                          ObjectProvider<GracefulStopLifecycle> stop,
                                          ObjectProvider<TerminationSummaryWriter> summary,
                                          ObjectProvider<BatchExitCodeGenerator> exitCodes,
                                          ObjectProvider<BatchRunEventPublisher> events,
                                          ObjectProvider<PartitionConsumer> partitions,
                                          BatchProperties properties, Environment environment) {
            return new AdharBatchRunner(() -> {
                ExecutionLease leases = lease.getIfAvailable(ExecutionLease::none);
                return new AdharBatchRunner.Collaborators(jobRepository.getObject(), jobOperator.getObject(),
                        jobResolver.getObject(), specs.getObject(), parametersResolver.getObject(),
                        contextResolver.getObject(), new RecoveryPolicy(leases, lookup.getObject(),
                        properties.getRecovery().getStaleAfter(), Clock.systemUTC()), leases, stop.getObject(),
                        summary.getObject(), exitCodes.getObject(), events.getObject(),
                        AdharBatchRunner.Collaborators.heartbeatThread(), partitions.getIfAvailable());
            }, properties, environment::getProperty, Clock.systemUTC(), AdharBatchRunner.Sleeper.threadSleep());
        }
    }

    /**
     * The embedded scheduler on its engine {@code simple}, when {@code adhar.batch.scheduler.enabled}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty(name = "adhar.batch.scheduler.enabled")
    @ConditionalOnProperty(name = "adhar.batch.scheduler.engine", havingValue = "simple", matchIfMissing = true)
    static class SchedulerConfiguration {

        @Bean
        @ConditionalOnMissingBean
        BatchScheduler batchScheduler(JobOperator jobOperator, JobRepository jobRepository, JobResolver jobResolver,
                                      ObjectProvider<SchedulerLock> schedulerLock,
                                      ObjectProvider<JobParameterSpecRegistry> specs,
                                      JobParametersResolver parametersResolver, BatchProperties properties,
                                      ObjectProvider<SchedulerMetrics> metrics) {
            BatchProperties.Scheduler settings = properties.getScheduler();
            SchedulerLock lock = schedulerLock.getIfAvailable();
            if (lock == null && settings.getLock() == BatchProperties.Scheduler.Lock.JDBC) {
                log.warn("adhar.batch.scheduler.lock=jdbc but there is no DataSource: the embedded scheduler runs "
                        + "without a lock, so only one instance of this service may run");
            }
            SimpleSchedulerEngine engine = new SimpleSchedulerEngine(jobOperator, jobRepository, jobResolver,
                    specs.getIfAvailable(), parametersResolver, lock, settings, properties.getShutdown().getTimeout(),
                    Clock.systemUTC());
            engine.observe(metrics.getIfAvailable(SchedulerMetrics::none), properties.getQuartz());
            return new BatchScheduler(engine);
        }
    }

    /**
     * The in-service scheduler's meters ({@code adhar.batch.scheduler.*}), on either engine, when
     * the application has a {@code MeterRegistry}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty(name = "adhar.batch.scheduler.enabled")
    @ConditionalOnClass(name = "io.micrometer.core.instrument.MeterRegistry")
    static class SchedulerMetricsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(type = "io.micrometer.core.instrument.MeterRegistry")
        SchedulerMetrics schedulerMetrics(io.micrometer.core.instrument.MeterRegistry registry) {
            return new MicrometerSchedulerMetrics(registry);
        }
    }

    /**
     * {@code adhar.batch.scheduler.engine=quartz} without Quartz on the classpath: the start fails
     * naming the dependency, rather than running no schedule at all.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnBooleanProperty(name = "adhar.batch.scheduler.enabled")
    @ConditionalOnProperty(name = "adhar.batch.scheduler.engine", havingValue = "quartz")
    @Conditional(QuartzMissingConfiguration.QuartzAbsent.class)
    static class QuartzMissingConfiguration {

        QuartzMissingConfiguration() {
            throw new IllegalStateException("adhar.batch.scheduler.engine=quartz needs Quartz on the classpath: add "
                    + "the dependency " + QuartzConfiguration.DEPENDENCY + " (managed by Spring Boot), or use the "
                    + "engine simple");
        }

        /**
         * Any of the classes the engine quartz needs is missing (Quartz itself, Spring's
         * {@code SchedulerFactoryBean}, or Spring Boot's Quartz auto-configuration).
         */
        static class QuartzAbsent implements Condition {

            @Override
            public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
                ClassLoader classLoader = context.getClassLoader();
                return !ClassUtils.isPresent(QuartzConfiguration.SCHEDULER_CLASS, classLoader)
                        || !ClassUtils.isPresent(QuartzConfiguration.FACTORY_BEAN_CLASS, classLoader)
                        || !ClassUtils.isPresent(QuartzConfiguration.CUSTOMIZER_CLASS, classLoader);
            }
        }
    }
}
