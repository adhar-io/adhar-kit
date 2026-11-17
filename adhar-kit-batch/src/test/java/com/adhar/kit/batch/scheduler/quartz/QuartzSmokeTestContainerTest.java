package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.QuartzContexts;
import com.adhar.kit.batch.support.QuartzJobs;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The engine {@code quartz} end to end on PostgreSQL 18: one Spring Boot application, the kit's two
 * migration streams, the clustered job store, and a runtime trigger that runs a real chunk job.
 */
@Testcontainers(disabledWithoutDocker = true)
class QuartzSmokeTestContainerTest {

    @Test
    @DisplayName("a service on the engine quartz starts on a fresh database and a now trigger runs its job once")
    void aNowTriggerRunsTheJob() throws Exception {
        String url = PostgresDatabases.createDatabase("qsmoke");
        try (ConfigurableApplicationContext context = QuartzContexts.start(QuartzContexts.properties(url, "a"),
                QuartzJobs.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            TestJobs.seed(jdbc, 35);
            QuartzContexts.fireNow(context, QuartzJobs.CHUNK_JOB, "smoke", "{\"run\":\"smoke\"}");
            await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.queryForObject(
                    "select count(*) from TARGET_ITEMS", Integer.class) == 35);
            await().atMost(Duration.ofSeconds(30)).until(() -> "COMPLETED".equals(jdbc.queryForObject(
                    "select status from batch.batch_job_execution", String.class)));

            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = 'batch' "
                    + "and table_name like 'qrtz\\_%'", Integer.class)).as("Quartz's tables, in the batch schema").isEqualTo(11);
            assertThat(jdbc.queryForList("select parameter_name from batch.batch_job_execution_params "
                    + "where identifying = 'Y'", String.class)).as("a now trigger's identity is the caller's parameters")
                    .containsExactly("run");
            assertThat(jdbc.queryForObject("select parameter_value from batch.batch_job_execution_params "
                    + "where parameter_name = ?", String.class, QuartzSchedulerEngine.TRIGGER_KEY_PARAMETER))
                    .isEqualTo(QuartzSchedulerEngine.RUNTIME_GROUP + ".smoke");
        }
    }
}
