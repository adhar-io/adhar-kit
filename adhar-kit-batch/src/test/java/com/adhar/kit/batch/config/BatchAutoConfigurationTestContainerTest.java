package com.adhar.kit.batch.config;

import com.adhar.kit.batch.BatchFacade;
import com.adhar.kit.batch.listener.BatchListenerAttacher;
import com.adhar.kit.batch.lock.JdbcSchedulerLock;
import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.metrics.BatchMetrics;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.repository.BatchSchemaMigrator;
import com.adhar.kit.batch.repository.ResourcelessRepositoryGuard;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.TestJobs;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.batch.jdbc.autoconfigure.BatchJdbcProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A0.1 (design 0025 §15): with the REAL auto-configuration ({@code @SpringBootTest}, not an
 * {@code ApplicationContextRunner} with hand-made beans, which is what hid K2) on PostgreSQL 18:
 * the JobRepository is JDBC, the {@code batch} schema exists, and {@link BatchOperator},
 * {@link SchedulerLock} and the kit gauges are beans.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = {BatchTestApplication.class, TestJobs.class})
class BatchAutoConfigurationTestContainerTest {

    private static final String URL = PostgresDatabases.createDatabase("a01");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.postgres(URL).forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    JobRepository jobRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    BatchOperator batchOperator;

    @Autowired
    @Qualifier(TestJobs.IMPORT_JOB)
    Job importJob;

    @Test
    void theJobRepositoryIsJdbcInTheKitsSchema() throws Exception {
        assertThat(ResourcelessRepositoryGuard.class).isNotNull();
        assertThat(context.getBean(BatchJdbcProperties.class).getTablePrefix()).isEqualTo("batch.BATCH_");

        TestJobs.seed(jdbcTemplate, 25);
        JobExecution execution = batchOperator.start(importJob, new JobParametersBuilder()
                .addString("run", "a01-repository").toJobParameters());

        assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        // Persisted in the batch schema, not in memory: a fresh query finds the instance.
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from batch.batch_job_instance where job_name = ?", Integer.class,
                TestJobs.IMPORT_JOB)).isEqualTo(1);
        assertThat(jobRepository.getJobExecution(execution.getId())).isNotNull();
        assertThat(jdbcTemplate.queryForObject("select count(*) from target_items", Integer.class)).isEqualTo(25);
    }

    @Test
    void theBatchSchemaIsMigratedByTheKit() {
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.schemata where schema_name = 'batch'", Integer.class))
                .isEqualTo(1);
        List<String> versions = jdbcTemplate.queryForList(
                "select version from batch.adhar_batch_schema_history where success order by installed_rank",
                String.class);
        assertThat(versions).containsExactly("6.0.0", "6.0.1");
        assertThat(jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'batch'", String.class))
                .contains("batch_job_instance", "batch_job_execution", "batch_job_execution_params",
                        "batch_job_execution_context", "batch_step_execution", "batch_step_execution_context",
                        "adhar_scheduler_lock", "adhar_batch_execution_lease", "adhar_batch_dead_letter");
        // Nothing of Spring Batch's leaks into the application's own schema.
        assertThat(jdbcTemplate.queryForObject("select count(*) from information_schema.tables "
                + "where table_schema = 'public' and table_name like 'batch\\_%'", Integer.class)).isZero();
        assertThat(context.getBean(BatchSchemaMigrator.class).getLayout().managed()).isTrue();
    }

    @Test
    void theOperatorTheLockAndTheGaugesAreBeans() throws Exception {
        assertThat(context.getBean(BatchOperator.class)).isNotNull();
        SchedulerLock lock = context.getBean(SchedulerLock.class);
        assertThat(lock).isInstanceOf(JdbcSchedulerLock.class);
        assertThat(((JdbcSchedulerLock) lock).getTable()).isEqualTo("batch.ADHAR_SCHEDULER_LOCK");
        assertThat(lock.tryLock("a01-lock", java.time.Duration.ofSeconds(30))).isTrue();
        lock.unlock("a01-lock");
        assertThat(context.getBean(BatchMetrics.class)).isNotNull();
        assertThat(context.getBean(AdharSteps.class)).isNotNull();
        assertThat(BatchFacade.getInstance().getOperator()).isSameAs(batchOperator);

        TestJobs.seed(jdbcTemplate, 15);
        batchOperator.start(importJob, new JobParametersBuilder().addString("run", "a01-gauges").toJobParameters());

        MeterRegistry registry = context.getBean(MeterRegistry.class);
        assertThat(registry.find(BatchMetrics.RUN).tag("job", TestJobs.IMPORT_JOB).gauge()).isNotNull();
        assertThat(registry.find(BatchMetrics.LAST_COMMIT).tag("step", "importStep").gauge()).isNotNull();
        assertThat(registry.find(BatchMetrics.LAST_COMMIT).tag("step", "importStep").gauge().value()).isPositive();
    }

    @Test
    void theKitsListenersAreAttachedToEveryJobAndStep() {
        BatchListenerAttacher attacher = context.getBean(BatchListenerAttacher.class);
        assertThat(attacher.isAttached(importJob)).isTrue();
        assertThat(attacher.isAttached(context.getBean("importStep"))).isTrue();
        assertThat(attacher.isAttached(context.getBean("slowStep"))).isTrue();
    }
}
