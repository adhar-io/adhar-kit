package com.adhar.kit.test.batch;

import com.adhar.kit.test.container.PostgresTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.repository.support.ResourcelessJobRepository;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AdharBatchTest} itself: a real auto-configured context on its own database of the
 * platform PostgreSQL, migrated by the kit, with Spring Batch's test utilities and no job run at
 * start-up; the test's own properties win.
 */
@AdharBatchTest(classes = CopyJobApplication.class, properties = {
        "spring.sql.init.mode=always", "spring.sql.init.schema-locations=classpath:batch/schema.sql",
        "spring.datasource.hikari.maximum-pool-size=3"})
@Testcontainers(disabledWithoutDocker = true)
class AdharBatchTestTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    JobRepository jobRepository;
    @Autowired
    JobOperatorTestUtils jobs;
    @Autowired
    JobRepositoryTestUtils repository;
    @Autowired
    ConfigurableEnvironment environment;

    @Test
    @DisplayName("the context runs on PostgreSQL 18.4 with the kit's batch schema and a JDBC JobRepository")
    void platformPostgresWithTheKitsSchema() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("18.4");
        assertThat(PostgresTestContainer.PLATFORM_IMAGE).isEqualTo("postgres:18.4");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'batch'"
                + " AND table_name IN ('batch_job_instance', 'adhar_batch_schema_history', 'adhar_batch_execution_lease')",
                Integer.class)).isEqualTo(3);
        assertThat(jobRepository).isNotInstanceOf(ResourcelessJobRepository.class);
        assertThat(environment.getProperty("spring.datasource.url")).startsWith("jdbc:postgresql://")
                .contains("/batch_test_");
    }

    @Test
    @DisplayName("Spring Batch's test utilities are wired, and no job ran at start-up")
    void testUtilitiesAndNoStartupRun() {
        assertThat(jobs.getJob().getName()).isEqualTo(CopyJobApplication.JOB);
        assertThat(jobs.getJobOperator()).isNotNull();
        assertThat(repository).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch.batch_job_execution", Integer.class)).isZero();
        assertThat(environment.getProperty("spring.batch.job.enabled")).isEqualTo("false");
    }

    @Test
    @DisplayName("the test's own properties win over the kit's")
    void theTestsPropertiesWin() {
        assertThat(environment.getProperty("spring.datasource.hikari.maximum-pool-size")).isEqualTo("3");
        assertThat(environment.getPropertySources().contains(AdharBatchTestContextCustomizerFactory.PROPERTY_SOURCE_NAME))
                .isTrue();
    }
}
