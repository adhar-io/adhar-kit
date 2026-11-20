package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.event.BatchRunEventPublisher;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.repository.BatchRepositoryLayout;
import com.adhar.kit.batch.repository.BatchSchemaMigrator;
import com.adhar.kit.batch.repository.QuartzSchemaMigrator;
import com.adhar.kit.batch.runtime.JdbcExecutionLease;
import com.adhar.kit.batch.runtime.KubernetesOwnerLookup;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.scheduler.ScheduleReportWriter;
import com.adhar.kit.batch.scheduler.SchedulerMetrics;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Scheduler;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchDataSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The engine {@code quartz} of the embedded scheduler (design 0025 §5.20.3, §5.20.4): active with
 * {@code adhar.batch.scheduler.enabled=true}, {@code adhar.batch.scheduler.engine=quartz} and
 * {@code spring-boot-starter-quartz} on the classpath (without it, the kit's auto-configuration
 * fails the start naming the dependency).
 *
 * <p>It runs the kit's second migration stream ({@link QuartzSchemaMigrator}), owns the Quartz
 * settings every guarantee of §5.20 rests on, and points Spring Boot's {@code SchedulerFactoryBean}
 * at the JobRepository's DataSource, so the job store, the JobRepository and the kit's tables share
 * one database and one schema:</p>
 *
 * <table>
 *   <caption>The settings the kit owns</caption>
 *   <tr><td>{@code spring.quartz.job-store-type}</td><td>{@code jdbc}</td></tr>
 *   <tr><td>{@code spring.quartz.jdbc.initialize-schema}</td><td>{@code never}</td></tr>
 *   <tr><td>{@code spring.quartz.overwrite-existing-jobs}</td><td>{@code true}</td></tr>
 *   <tr><td>{@code spring.quartz.wait-for-jobs-to-complete-on-shutdown}</td><td>{@code false}</td></tr>
 *   <tr><td>{@code org.quartz.jobStore.driverDelegateClass}</td><td>{@code PostgreSQLDelegate}</td></tr>
 *   <tr><td>{@code org.quartz.jobStore.tablePrefix}</td><td>{@code <schema>.QRTZ_}</td></tr>
 *   <tr><td>{@code org.quartz.jobStore.isClustered}, {@code acquireTriggersWithinLock},
 *       {@code useProperties}</td><td>{@code true}</td></tr>
 *   <tr><td>{@code org.quartz.jobStore.misfireThreshold}</td><td>{@code adhar.batch.scheduler.misfire-threshold}</td></tr>
 *   <tr><td>{@code org.quartz.jobStore.clusterCheckinInterval}</td><td>{@code adhar.batch.quartz.cluster-checkin-interval}</td></tr>
 *   <tr><td>{@code org.quartz.scheduler.instanceName}</td><td>{@code adhar.batch.quartz.scheduler-name}</td></tr>
 *   <tr><td>{@code org.quartz.scheduler.instanceId}</td><td>{@code AUTO}</td></tr>
 *   <tr><td>{@code org.quartz.threadPool.threadCount}</td><td>{@code adhar.batch.scheduler.pool-size}</td></tr>
 *   <tr><td>{@code SchedulerFactoryBean} phase</td><td>{@code Integer.MAX_VALUE - 1}</td></tr>
 * </table>
 *
 * <p>An application value that contradicts one fails the start, naming the property: each is
 * what a guarantee rests on (a store that is not clustered fires every trigger on every replica; a
 * prefix outside the batch schema leaves the kit's migration history behind). An application that
 * already runs Quartz on another store or prefix is refused the same way, not migrated silently:
 * it moves its own jobs into the kit's store, where they share the scheduler (the job group
 * {@value QuartzSchedulerEngine#JOB_GROUP} and the trigger groups
 * {@value QuartzSchedulerEngine#CONFIG_GROUP} and {@value QuartzSchedulerEngine#RUNTIME_GROUP} are
 * reserved), or stays on {@code simple}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = {QuartzConfiguration.SCHEDULER_CLASS, QuartzConfiguration.FACTORY_BEAN_CLASS,
        QuartzConfiguration.CUSTOMIZER_CLASS})
@ConditionalOnBooleanProperty(name = "adhar.batch.scheduler.enabled")
@ConditionalOnProperty(name = "adhar.batch.scheduler.engine", havingValue = "quartz")
public class QuartzConfiguration {

    /** Quartz's scheduler. */
    public static final String SCHEDULER_CLASS = "org.quartz.Scheduler";

    /** Spring's factory of it. */
    public static final String FACTORY_BEAN_CLASS = "org.springframework.scheduling.quartz.SchedulerFactoryBean";

    /** Spring Boot's Quartz auto-configuration, through its customizer. */
    public static final String CUSTOMIZER_CLASS = "org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer";

    /** The dependency the engine needs. */
    public static final String DEPENDENCY = "org.springframework.boot:spring-boot-starter-quartz";

    /** The phase of Spring Boot's {@code SchedulerFactoryBean}: just before the kit's lifecycle, so it stops after it. */
    public static final int SCHEDULER_PHASE = Integer.MAX_VALUE - 1;

    static final String QUARTZ_PROPERTIES = "spring.quartz.properties";

    private final Map<String, String> owned;

    /**
     * @param environment the application's environment, read for contradicting values
     * @param properties  {@code adhar.batch.*}
     * @throws IllegalStateException naming the first property whose value contradicts the kit's
     */
    public QuartzConfiguration(Environment environment, BatchProperties properties) {
        String schedulerName = schedulerName(properties, environment);
        this.owned = ownedQuartzProperties(properties, schedulerName);
        verify(environment, properties, schedulerName, owned);
    }

    /**
     * @param properties  {@code adhar.batch.*}
     * @param environment the environment, for {@code spring.application.name}
     * @return the cluster's identity
     * @throws IllegalStateException when neither names it
     */
    static String schedulerName(BatchProperties properties, Environment environment) {
        String name = properties.getQuartz().getSchedulerName();
        if (!StringUtils.hasText(name)) {
            name = environment.getProperty("spring.application.name");
        }
        if (!StringUtils.hasText(name)) {
            throw new IllegalStateException("adhar.batch.quartz.scheduler-name is required with "
                    + "adhar.batch.scheduler.engine=quartz (or spring.application.name): it is the cluster's identity, "
                    + "equal across one service's replicas and distinct per service");
        }
        return name;
    }

    /**
     * @return the {@code org.quartz.*} values the kit sets, by key
     */
    static Map<String, String> ownedQuartzProperties(BatchProperties properties, String schedulerName) {
        Map<String, String> owned = new LinkedHashMap<>();
        owned.put("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        owned.put("org.quartz.jobStore.tablePrefix", properties.getRepository().getSchema() + "."
                + QuartzSchemaMigrator.TABLE_PREFIX);
        owned.put("org.quartz.jobStore.isClustered", "true");
        owned.put("org.quartz.jobStore.acquireTriggersWithinLock", "true");
        owned.put("org.quartz.jobStore.useProperties", "true");
        owned.put("org.quartz.jobStore.misfireThreshold",
                Long.toString(properties.getScheduler().getMisfireThreshold().toMillis()));
        owned.put("org.quartz.jobStore.clusterCheckinInterval",
                Long.toString(properties.getQuartz().getClusterCheckinInterval().toMillis()));
        owned.put("org.quartz.scheduler.instanceName", schedulerName);
        owned.put("org.quartz.scheduler.instanceId", "AUTO");
        owned.put("org.quartz.threadPool.threadCount", Integer.toString(properties.getScheduler().getPoolSize()));
        return owned;
    }

    private static void verify(Environment environment, BatchProperties properties, String schedulerName,
                               Map<String, String> owned) {
        Binder binder = Binder.get(environment);
        requireValue(binder, "spring.quartz.job-store-type", "jdbc",
                "the job store must be the clustered JDBC store in the batch schema");
        requireValue(binder, "spring.quartz.jdbc.initialize-schema", "never",
                "the kit's Flyway stream owns the Quartz tables; Spring Boot's initializer would create unqualified "
                        + "tables in the connection's default schema, outside the kit's history");
        requireValue(binder, "spring.quartz.overwrite-existing-jobs", "true",
                "configured triggers follow the properties at every start");
        requireValue(binder, "spring.quartz.wait-for-jobs-to-complete-on-shutdown", "false",
                "the kit's own lifecycle stops the runs first and re-queues them");
        binder.bind("spring.quartz.scheduler-name", String.class).ifBound(name -> {
            if (!name.equals(schedulerName)) {
                throw new IllegalStateException("spring.quartz.scheduler-name is '" + name + "', which contradicts "
                        + "adhar.batch.quartz.scheduler-name ('" + schedulerName + "'): the kit names the scheduler; "
                        + "remove spring.quartz.scheduler-name");
            }
        });
        Map<String, String> application = binder.bind(QUARTZ_PROPERTIES,
                Bindable.mapOf(String.class, String.class)).orElseGet(Map::of);
        owned.forEach((key, value) -> {
            String configured = application.get(key);
            if (configured != null && !sameValue(key, configured, value)) {
                throw new IllegalStateException(QUARTZ_PROPERTIES + "." + key + " is '" + configured + "', but the kit "
                        + "sets it to '" + value + "' with adhar.batch.scheduler.engine=quartz (design 0025 §5.20.4)"
                        + hint(key) + ". Remove the property.");
            }
        });
        String store = application.get("org.quartz.jobStore.class");
        if (store != null && !store.equals("org.springframework.scheduling.quartz.LocalDataSourceJobStore")) {
            throw new IllegalStateException(QUARTZ_PROPERTIES + ".org.quartz.jobStore.class is '" + store + "': the "
                    + "engine quartz runs on the kit's clustered JDBC store in the batch schema. Move this application's "
                    + "own Quartz jobs into it, or stay on adhar.batch.scheduler.engine=simple. Remove the property.");
        }
        log.debug("Quartz settings verified for scheduler '{}' in schema '{}'", schedulerName,
                properties.getRepository().getSchema());
    }

    private static void requireValue(Binder binder, String key, String expected, String why) {
        binder.bind(key, String.class).ifBound(value -> {
            if (!value.strip().equalsIgnoreCase(expected)) {
                throw new IllegalStateException(key + " is '" + value + "', but adhar.batch.scheduler.engine=quartz "
                        + "needs '" + expected + "': " + why + " (design 0025 §5.20.4). Remove the property.");
            }
        });
    }

    private static boolean sameValue(String key, String configured, String owned) {
        String value = configured.strip();
        if (key.endsWith("tablePrefix") || key.endsWith("isClustered") || key.endsWith("acquireTriggersWithinLock")
                || key.endsWith("useProperties") || key.endsWith("instanceId")) {
            // PostgreSQL folds the unquoted prefix to lower case; Quartz parses booleans and AUTO
            // case-insensitively.
            return value.toLowerCase(Locale.ROOT).equals(owned.toLowerCase(Locale.ROOT));
        }
        return value.equals(owned);
    }

    private static String hint(String key) {
        if (key.endsWith("tablePrefix")) {
            return ": the Quartz tables live in the batch schema under a fixed prefix, created by a released migration";
        }
        if (key.endsWith("isClustered") || key.endsWith("acquireTriggersWithinLock")) {
            return ": a store that is not clustered fires every trigger on every replica";
        }
        if (key.endsWith("useProperties")) {
            return ": the JobDataMap holds strings only, so a class change never breaks a stored trigger";
        }
        return "";
    }

    /**
     * @param dataSource      the primary DataSource
     * @param batchDataSource the dedicated batch DataSource, if any
     * @param layout          where the repository lives
     * @param mainStream      the main stream, which must run first
     * @param properties      {@code adhar.batch.*}
     * @return the Quartz stream's migrator
     */
    @Bean
    @ConditionalOnMissingBean
    QuartzSchemaMigrator quartzSchemaMigrator(DataSource dataSource,
                                              @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                              BatchRepositoryLayout layout, BatchSchemaMigrator mainStream,
                                              BatchProperties properties) {
        return new QuartzSchemaMigrator(batchDataSource.getIfAvailable(() -> dataSource), layout,
                properties.getRepository().isMigrate(), mainStream);
    }

    /**
     * The kit's {@code SchedulerFactoryBeanCustomizer}, last after Spring Boot's own: the
     * JobRepository's DataSource, the owned settings merged over the application's, the scheduler's
     * name and its phase. It takes the migrator, so the scheduler is never created before its tables.
     *
     * @return the customizer
     */
    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE)
    SchedulerFactoryBeanCustomizer adharBatchSchedulerCustomizer(DataSource dataSource,
                                                                 @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                                                 QuartzProperties quartzProperties,
                                                                 QuartzSchemaMigrator migrator,
                                                                 BatchProperties properties,
                                                                 Environment environment) {
        DataSource store = batchDataSource.getIfAvailable(() -> dataSource);
        String schedulerName = schedulerName(properties, environment);
        return factory -> {
            factory.setDataSource(store);
            Properties merged = new Properties();
            merged.putAll(quartzProperties.getProperties());
            merged.putAll(owned);
            factory.setQuartzProperties(merged);
            factory.setSchedulerName(schedulerName);
            factory.setOverwriteExistingJobs(true);
            factory.setWaitForJobsToCompleteOnShutdown(false);
            factory.setPhase(SCHEDULER_PHASE);
        };
    }

    /**
     * @return the run observer writing the execution lease of this replica's runs
     */
    @Bean
    @ConditionalOnMissingBean
    QuartzSchedulerEngine.Ownership quartzRunOwnership(DataSource dataSource,
                                                       @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                                       BatchRepositoryLayout layout) {
        JdbcTemplate jdbc = new JdbcTemplate(batchDataSource.getIfAvailable(() -> dataSource));
        return new QuartzSchedulerEngine.Ownership(new JdbcExecutionLease(jdbc,
                layout.kitTable(BatchRepositoryLayout.EXECUTION_LEASE_TABLE)));
    }

    /**
     * The engine {@code quartz}, with its business calendars ({@code adhar.batch.quartz.calendars.*}),
     * its meters, its schedule report and the publisher of its {@code Skipped}/{@code Recovered} events.
     *
     * @return the engine {@code quartz}
     */
    @Bean
    @ConditionalOnMissingBean
    QuartzSchedulerEngine quartzSchedulerEngine(Scheduler scheduler, BatchOperator operator, JobRepository repository,
                                                ListableBeanFactory beanFactory,
                                                ObjectProvider<JobParameterSpecRegistry> specs,
                                                JobParametersResolver resolver,
                                                QuartzSchedulerEngine.Ownership ownership,
                                                DataSource dataSource,
                                                @BatchDataSource ObjectProvider<DataSource> batchDataSource,
                                                BatchRepositoryLayout layout, BatchProperties properties,
                                                ObjectProvider<SchedulerMetrics> metrics,
                                                ObjectProvider<BatchRunEventPublisher> events,
                                                Environment environment) {
        Map<String, Job> jobs = beanFactory.getBeansOfType(Job.class);
        String schedulerName = schedulerName(properties, environment);
        String application = environment.getProperty("spring.application.name", schedulerName);
        CalendarRegistry calendars = new CalendarRegistry(CalendarRegistry.bind(environment),
                properties.getScheduler().getTimeZone());
        Clock clock = Clock.systemUTC();
        QuartzSchedulerEngine engine = new QuartzSchedulerEngine(scheduler, operator, repository, jobs,
                specs.getIfAvailable(), resolver, ownership,
                new JdbcTemplate(batchDataSource.getIfAvailable(() -> dataSource)),
                QuartzSchemaMigrator.tablePrefix(layout), properties, application,
                environment.getProperty("spring.application.version"), calendars, clock);
        SchedulerMetrics meters = metrics.getIfAvailable(SchedulerMetrics::none);
        engine.observe(meters, reportWriter(properties.getQuartz().getReport(), schedulerName, application,
                engine, meters, clock, KubernetesOwnerLookup.access(System::getenv,
                        KubernetesOwnerLookup.SERVICE_ACCOUNT)), events.getIfAvailable());
        return engine;
    }

    /**
     * The schedule report (design 0025 §5.20.8): on by default only in Kubernetes, where the pod's
     * ServiceAccount can write it.
     *
     * @return the writer, {@code null} when the report is off or cannot be written from here
     */
    static ScheduleReportWriter reportWriter(BatchProperties.Quartz.Report settings, String schedulerName,
                                             String application, QuartzSchedulerEngine engine,
                                             SchedulerMetrics metrics, Clock clock,
                                             Optional<KubernetesOwnerLookup.ApiAccess> api) {
        boolean enabled = settings.getEnabled() != null ? settings.getEnabled() : api.isPresent();
        if (!enabled) {
            return null;
        }
        String name = StringUtils.hasText(settings.getName()) ? settings.getName()
                : ScheduleReportWriter.defaultName(schedulerName);
        if (!ScheduleReportWriter.isConfigMapName(name)) {
            throw new IllegalStateException("adhar.batch.quartz.report.name '" + name + "' is not a ConfigMap name "
                    + "(lower-case letters, digits, '-' and '.'): the API server would refuse every report");
        }
        if (api.isEmpty()) {
            log.info("adhar.batch.quartz.report.enabled=true, but this process runs outside Kubernetes (no "
                    + "KUBERNETES_SERVICE_HOST or ServiceAccount token): the schedule report {} is not written", name);
            return null;
        }
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "adhar-batch-schedule-report");
            thread.setDaemon(true);
            return thread;
        });
        return new ScheduleReportWriter(api.get(), name, application, settings.getMinInterval(), engine::buildReport,
                metrics, clock, executor);
    }

    /**
     * Runtime triggers (design 0025 §5.20.5 rules 5-8): a bean only on the engine {@code quartz}.
     *
     * @param engine the engine
     * @return {@code fireNow}, {@code fireAt}, {@code cancel}, {@code list}
     */
    @Bean
    @ConditionalOnMissingBean
    BatchTriggers batchTriggers(QuartzSchedulerEngine engine) {
        return new BatchTriggers(engine);
    }

    /**
     * @param engine the engine {@code quartz}
     * @return the embedded scheduler on it
     */
    @Bean
    @ConditionalOnMissingBean
    BatchScheduler batchScheduler(QuartzSchedulerEngine engine) {
        return new BatchScheduler(engine);
    }
}
