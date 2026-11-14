package com.adhar.kit.batch.scheduler.quartz;

import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.QuartzContexts;
import com.adhar.kit.batch.support.QuartzJobs;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers(disabledWithoutDocker = true)
class QuartzSmokeTestContainerTest {

    @Test
    void smoke() throws Exception {
        String url = PostgresDatabases.createDatabase("qsmoke");
        try (ConfigurableApplicationContext context = QuartzContexts.start(debug(QuartzContexts.properties(url, "a")),
                QuartzJobs.class)) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            TestJobs.seed(jdbc, 35);
            QuartzContexts.fireNow(context, QuartzJobs.CHUNK_JOB, "smoke", "{\"run\":\"smoke\"}");
            await().atMost(Duration.ofSeconds(30)).until(() -> jdbc.queryForObject(
                    "select count(*) from TARGET_ITEMS", Integer.class) == 35);
            System.out.println("TABLES " + jdbc.queryForList("select table_name from information_schema.tables where table_schema='batch' order by 1", String.class));
            System.out.println("PARAMS " + jdbc.queryForList("select parameter_name, parameter_value, identifying from batch.batch_job_execution_params"));
        }
    }

    static java.util.Map<String, Object> debug(java.util.Map<String, Object> p) {
        p.put("logging.level.org.flywaydb.core.internal.sqlscript", "DEBUG");
        return p;
    }
}
