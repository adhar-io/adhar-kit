package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.config.BatchProperties;
import com.adhar.kit.batch.event.BatchRunEventPublisher;
import com.adhar.kit.batch.scheduler.SchedulerMetrics;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.params.JobParameterSpecRegistry;
import com.adhar.kit.batch.params.JobParametersResolver;
import com.adhar.kit.batch.repository.BatchRepositoryLayout;
import com.adhar.kit.batch.repository.BatchSchemaMigrationException;
import com.adhar.kit.batch.runtime.ExecutionLease;
import com.adhar.kit.batch.scheduler.BatchScheduler;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.quartz.Scheduler;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.quartz.autoconfigure.QuartzProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuartzConfigurationTest {

    @Test
    void schedulerNamePrefersTheExplicitBatchProperty() {
        BatchProperties properties = new BatchProperties();
        properties.getQuartz().setSchedulerName("orders");

        assertThat(QuartzConfiguration.schedulerName(properties, new MockEnvironment()
                .withProperty("spring.application.name", "payments")))
                .isEqualTo("orders");
    }

    @Test
    void schedulerNameFallsBackToTheApplicationName() {
        BatchProperties properties = new BatchProperties();

        assertThat(QuartzConfiguration.schedulerName(properties, new MockEnvironment()
                .withProperty("spring.application.name", "shipping")))
                .isEqualTo("shipping");
    }

    @Test
    void schedulerNameRequiresANameWhenNeitherPropertyIsProvided() {
        BatchProperties properties = new BatchProperties();

        assertThatThrownBy(() -> QuartzConfiguration.schedulerName(properties, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.batch.quartz.scheduler-name");
    }

    @Test
    void ownedQuartzPropertiesContainTheKitSettingsForTheClusteredJdbcStore() {
        BatchProperties properties = new BatchProperties();
        properties.getRepository().setSchema("batch");
        properties.getScheduler().setPoolSize(6);
        properties.getScheduler().setMisfireThreshold(Duration.ofMinutes(2));
        properties.getQuartz().setClusterCheckinInterval(Duration.ofSeconds(30));

        Map<String, String> owned = QuartzConfiguration.ownedQuartzProperties(properties, "orders");

        assertThat(owned)
                .containsEntry("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate")
                .containsEntry("org.quartz.jobStore.tablePrefix", "batch.QRTZ_")
                .containsEntry("org.quartz.jobStore.isClustered", "true")
                .containsEntry("org.quartz.scheduler.instanceName", "orders")
                .containsEntry("org.quartz.threadPool.threadCount", "6");
    }

    // ------------------------------------------------------------------
    // The settings the kit owns (design 0025 §5.20.4)
    // ------------------------------------------------------------------

    private static BatchProperties orders() {
        BatchProperties properties = new BatchProperties();
        properties.getRepository().setSchema("batch");
        properties.getQuartz().setSchedulerName("orders");
        return properties;
    }

    /** A service's environment: the engine quartz refuses one without {@code spring.application.name}. */
    private static MockEnvironment named() {
        return new MockEnvironment().withProperty("spring.application.name", "orders-service");
    }

    private static void assertRefused(String property, String value, String... message) {
        assertThatThrownBy(() -> new QuartzConfiguration(named().withProperty(property, value), orders()))
                .as(property + "=" + value).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(property + " is '" + value + "'").hasMessageContaining("Remove the property")
                .hasMessageContainingAll(message);
    }

    @Test
    @DisplayName("an application's Spring Boot Quartz setting that contradicts the kit's fails the start, naming it and why")
    void contradictingSpringQuartzSettingsAreRefused() {
        assertRefused("spring.quartz.job-store-type", "memory", "needs 'jdbc'", "clustered JDBC store in the batch schema");
        assertRefused("spring.quartz.jdbc.initialize-schema", "always", "needs 'never'", "Flyway stream owns the Quartz tables");
        assertRefused("spring.quartz.overwrite-existing-jobs", "false", "needs 'true'", "follow the properties at every start");
        assertRefused("spring.quartz.wait-for-jobs-to-complete-on-shutdown", "true", "needs 'false'",
                "the kit's own lifecycle stops the runs first");
        assertThatThrownBy(() -> new QuartzConfiguration(named()
                .withProperty("spring.quartz.scheduler-name", "payments"), orders()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.quartz.scheduler-name is 'payments'")
                .hasMessageContaining("adhar.batch.quartz.scheduler-name ('orders')");
    }

    @Test
    @DisplayName("an org.quartz property that contradicts the kit's is refused with what the guarantee rests on")
    void contradictingQuartzPropertiesAreRefused() {
        String prefix = "spring.quartz.properties.";
        assertRefused(prefix + "org.quartz.jobStore.isClustered", "false", "the kit sets it to 'true'",
                "a store that is not clustered fires every trigger on every replica");
        assertRefused(prefix + "org.quartz.jobStore.acquireTriggersWithinLock", "false", "fires every trigger on every replica");
        assertRefused(prefix + "org.quartz.jobStore.tablePrefix", "QRTZ_", "the kit sets it to 'batch.QRTZ_'",
                "live in the batch schema under a fixed prefix");
        assertRefused(prefix + "org.quartz.jobStore.useProperties", "false", "the JobDataMap holds strings only");
        assertRefused(prefix + "org.quartz.threadPool.threadCount", "64", "the kit sets it to '"
                + new BatchProperties().getScheduler().getPoolSize() + "'");
        assertRefused(prefix + "org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate",
                "PostgreSQLDelegate");
        assertRefused(prefix + "org.quartz.scheduler.instanceName", "Orders", "the kit sets it to 'orders'");
        assertRefused(prefix + "org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore",
                "Move this application's own Quartz jobs into it", "adhar.batch.scheduler.engine=simple");
    }

    @Test
    @DisplayName("the same values, however an application spells them, and settings the kit does not own are accepted")
    void agreeingSettingsAreAccepted() {
        MockEnvironment environment = named()
                .withProperty("spring.quartz.job-store-type", " JDBC ")
                .withProperty("spring.quartz.jdbc.initialize-schema", "never")
                .withProperty("spring.quartz.overwrite-existing-jobs", "true")
                .withProperty("spring.quartz.wait-for-jobs-to-complete-on-shutdown", "false")
                .withProperty("spring.quartz.scheduler-name", "orders")
                .withProperty("spring.quartz.properties.org.quartz.jobStore.tablePrefix", "BATCH.qrtz_")
                .withProperty("spring.quartz.properties.org.quartz.jobStore.isClustered", "TRUE")
                .withProperty("spring.quartz.properties.org.quartz.scheduler.instanceId", "auto")
                .withProperty("spring.quartz.properties.org.quartz.scheduler.instanceName", "orders")
                .withProperty("spring.quartz.properties.org.quartz.jobStore.class",
                        "org.springframework.scheduling.quartz.LocalDataSourceJobStore")
                .withProperty("spring.quartz.properties.org.quartz.scheduler.idleWaitTime", "1000");

        assertThatCode(() -> new QuartzConfiguration(environment, orders())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("without a scheduler name the configuration fails: the cluster has no identity")
    void theConfigurationNeedsASchedulerName() {
        assertThatThrownBy(() -> new QuartzConfiguration(new MockEnvironment(), new BatchProperties()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("it is the cluster's identity");
    }

    @Test
    @DisplayName("without spring.application.name the start is refused, naming the property: two unnamed services on "
            + "one scheduler name would delete each other's jobs as retired")
    void theConfigurationNeedsAnApplicationName() {
        for (MockEnvironment unnamed : new MockEnvironment[] {new MockEnvironment(),
                new MockEnvironment().withProperty("spring.application.name", "  ")}) {
            assertThatThrownBy(() -> new QuartzConfiguration(unnamed, orders()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("spring.application.name is required with adhar.batch.scheduler.engine=quartz")
                    .hasMessageContaining("adhar.batch.quartz.scheduler-name 'orders'")
                    .hasMessageContaining("deleted as retired").hasMessageContaining("FQ4")
                    .as("the name that keeps the jobs of a service that already ran unnamed")
                    .hasMessageContaining("recorded 'orders' as the owner of its jobs: name it that");
        }
        // The scheduler's name may still come from the application's: one property then names both.
        assertThatCode(() -> new QuartzConfiguration(named(), new BatchProperties())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the owner recorded on every Quartz job is spring.application.name, never the scheduler name two services may share")
    void theOwnerIsTheApplicationNotTheScheduler() throws Exception {
        assertThat(storedOwner(named())).isEqualTo("orders-service");
        assertThatThrownBy(() -> storedOwner(new MockEnvironment())).as("no fallback to the scheduler name 'orders'")
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("spring.application.name");
    }

    /** The application the engine bean writes on a JobDetail, built as the configuration builds it. */
    private static String storedOwner(MockEnvironment environment) throws Exception {
        EmbeddedDatabase h2 = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("quartz-owner-" + System.nanoTime()).addScript("classpath:adhar-batch-test/h2-schema.sql").build();
        try {
            BatchProperties properties = orders();
            // The bean method alone: the constructor's refusal is theConfigurationNeedsAnApplicationName's.
            QuartzConfiguration configuration = new QuartzConfiguration(named(), properties);
            StaticListableBeanFactory none = new StaticListableBeanFactory();
            Job job = mock(Job.class);
            when(job.getName()).thenReturn("importJob");
            StaticListableBeanFactory beans = new StaticListableBeanFactory();
            beans.addBean("importJob", job);
            Scheduler scheduler = mock(Scheduler.class);
            when(scheduler.getSchedulerInstanceId()).thenReturn("replica-a");
            QuartzSchedulerEngine engine = configuration.quartzSchedulerEngine(scheduler, mock(BatchOperator.class),
                    mock(JobRepository.class), beans, none.getBeanProvider(JobParameterSpecRegistry.class),
                    mock(JobParametersResolver.class), configuration.quartzRunOwnership(h2,
                            none.getBeanProvider(DataSource.class), new BatchRepositoryLayout(false, "H2", "", "BATCH_")),
                    h2, none.getBeanProvider(DataSource.class),
                    new BatchRepositoryLayout(true, "PostgreSQL", "batch", "batch.BATCH_"), properties,
                    none.getBeanProvider(SchedulerMetrics.class), none.getBeanProvider(BatchRunEventPublisher.class),
                    environment);
            return engine.jobDetail("importJob").getJobDataMap().getString(QuartzSchedulerEngine.APPLICATION);
        } finally {
            h2.shutdown();
        }
    }

    @Test
    @DisplayName("the owned settings carry the misfire threshold, the check-in interval and the fixed store settings")
    void theOwnedSettings() {
        BatchProperties properties = orders();
        properties.getScheduler().setMisfireThreshold(Duration.ofMinutes(2));
        properties.getQuartz().setClusterCheckinInterval(Duration.ofSeconds(30));

        assertThat(QuartzConfiguration.ownedQuartzProperties(properties, "orders"))
                .containsEntry("org.quartz.jobStore.misfireThreshold", "120000")
                .containsEntry("org.quartz.jobStore.clusterCheckinInterval", "30000")
                .containsEntry("org.quartz.jobStore.acquireTriggersWithinLock", "true")
                .containsEntry("org.quartz.jobStore.useProperties", "true")
                .containsEntry("org.quartz.scheduler.instanceId", "AUTO")
                .hasSize(10);
    }

    // ------------------------------------------------------------------
    // The beans
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the customizer points Spring Boot's scheduler at the batch DataSource and lays the owned settings over the application's")
    void theCustomizer() {
        MockEnvironment environment = named();
        BatchProperties properties = orders();
        QuartzConfiguration configuration = new QuartzConfiguration(environment, properties);
        DataSource primary = mock(DataSource.class);
        DataSource batch = mock(DataSource.class);
        StaticListableBeanFactory dedicated = new StaticListableBeanFactory();
        dedicated.addBean("batchDataSource", batch);
        QuartzProperties application = new QuartzProperties();
        application.getProperties().put("org.quartz.scheduler.idleWaitTime", "1000");
        application.getProperties().put("org.quartz.jobStore.isClustered", "TRUE");
        SchedulerFactoryBean factory = mock(SchedulerFactoryBean.class);
        SchedulerFactoryBean onPrimary = mock(SchedulerFactoryBean.class);

        configuration.adharBatchSchedulerCustomizer(primary, dedicated.getBeanProvider(DataSource.class), application,
                null, properties, environment).customize(factory);
        configuration.adharBatchSchedulerCustomizer(primary, new StaticListableBeanFactory().getBeanProvider(DataSource.class),
                application, null, properties, environment).customize(onPrimary);

        verify(factory).setDataSource(batch);
        verify(onPrimary).setDataSource(primary);
        ArgumentCaptor<Properties> merged = ArgumentCaptor.forClass(Properties.class);
        verify(factory).setQuartzProperties(merged.capture());
        assertThat(merged.getValue()).containsEntry("org.quartz.scheduler.idleWaitTime", "1000")
                .containsEntry("org.quartz.jobStore.isClustered", "true")
                .containsEntry("org.quartz.jobStore.tablePrefix", "batch.QRTZ_")
                .containsEntry("org.quartz.scheduler.instanceName", "orders");
        verify(factory).setSchedulerName("orders");
        verify(factory).setOverwriteExistingJobs(true);
        verify(factory).setWaitForJobsToCompleteOnShutdown(false);
        verify(factory).setPhase(Integer.MAX_VALUE - 1);
    }

    @Test
    @DisplayName("the engine and its run ownership are built on the JobRepository's database: the lease names the Quartz instance")
    void theEngineBeans() throws Exception {
        EmbeddedDatabase h2 = new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2)
                .setName("quartz-beans-" + System.nanoTime()).addScript("classpath:adhar-batch-test/h2-schema.sql").build();
        try {
            MockEnvironment environment = named().withProperty("spring.application.version", "1.4.0");
            BatchProperties properties = orders();
            QuartzConfiguration configuration = new QuartzConfiguration(environment, properties);
            BatchRepositoryLayout layout = new BatchRepositoryLayout(false, "H2", "", "BATCH_");
            StaticListableBeanFactory none = new StaticListableBeanFactory();
            Job job = mock(Job.class);
            when(job.getName()).thenReturn("importJob");
            StaticListableBeanFactory beans = new StaticListableBeanFactory();
            beans.addBean("importJob", job);
            Scheduler scheduler = mock(Scheduler.class);
            when(scheduler.getSchedulerInstanceId()).thenReturn("replica-a");

            QuartzSchedulerEngine.Ownership ownership = configuration.quartzRunOwnership(h2,
                    none.getBeanProvider(DataSource.class), layout);
            QuartzSchedulerEngine engine = configuration.quartzSchedulerEngine(scheduler, mock(BatchOperator.class),
                    mock(JobRepository.class), beans, none.getBeanProvider(JobParameterSpecRegistry.class),
                    mock(JobParametersResolver.class), ownership, h2, none.getBeanProvider(DataSource.class),
                    new BatchRepositoryLayout(true, "PostgreSQL", "batch", "batch.BATCH_"), properties,
                    none.getBeanProvider(SchedulerMetrics.class), none.getBeanProvider(BatchRunEventPublisher.class),
                    environment);
            BatchScheduler batchScheduler = configuration.batchScheduler(engine);

            assertThat(batchScheduler.engine()).isSameAs(engine);
            assertThat(engine.name()).isEqualTo("quartz");
            assertThat(engine.job("importJob")).isSameAs(job);
            assertThat(engine.scheduleNameOf("importJob")).isEqualTo("import-job");
            assertThat(engine.appVersion()).isEqualTo("1.4.0");
            assertThat(engine.spec(job).jobName()).as("no declared parameters without a registry").isEqualTo("importJob");
            assertThat(engine.instanceId()).isEqualTo("replica-a");
            // The engine reads check-ins from <schema>.QRTZ_SCHEDULER_STATE, which this H2 does not
            // have: unreadable means alive, never a second run.
            assertThat(engine.instanceAlive("replica-b")).isTrue();

            QuartzSchedulerEngine.LocalRun run = new QuartzSchedulerEngine.LocalRun("importJob", "importJob",
                    "import-job-0a1b2c3d", ZoneOffset.UTC);
            ownership.begin(run, new ExecutionLease.Owner(engine.instanceId(), "", ""));
            ownership.claim(new JobExecution(41L, new JobInstance(7L, "importJob"), new JobParameters()));
            ownership.end();
            assertThat(new JdbcTemplate(h2).queryForObject("SELECT OWNER_POD FROM ADHAR_BATCH_EXECUTION_LEASE "
                    + "WHERE JOB_EXECUTION_ID = 41", String.class)).isEqualTo("replica-a");
            assertThat(ownership.owner(41L)).isEqualTo("replica-a");
        } finally {
            h2.shutdown();
        }
    }

    @Test
    @DisplayName("the engine quartz on a database that is not PostgreSQL fails the start, saying so")
    void theEngineNeedsPostgres() {
        Map<String, Object> properties = BatchTestApplication.h2();
        properties.put("adhar.batch.scheduler.enabled", "true");
        properties.put("adhar.batch.scheduler.engine", "quartz");
        properties.put("adhar.batch.quartz.scheduler-name", "orders");
        properties.put("spring.application.name", "orders-service");

        assertThatThrownBy(() -> BatchTestApplication.builder(TestJobs.class).properties(properties).run().close())
                .hasRootCauseInstanceOf(BatchSchemaMigrationException.class)
                .rootCause().hasMessageContaining("adhar.batch.scheduler.engine=quartz needs the JobRepository on PostgreSQL")
                .hasMessageContaining("it is on H2");
    }
}
