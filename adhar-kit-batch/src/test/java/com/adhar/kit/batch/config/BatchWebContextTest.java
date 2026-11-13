package com.adhar.kit.batch.config;

import com.adhar.kit.batch.lock.SchedulerLock;
import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.repository.BatchSchemaMigrator;
import com.adhar.kit.batch.step.AdharSteps;
import com.adhar.kit.batch.support.BatchTestApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A0.2 (design 0025 §15): a web service (what {@code springboot-web-service} scaffolds) that has
 * the kit on its classpath but declares no job gets none of the batch beans and no
 * {@code adhar_scheduler_lock} table. A servlet web context with the REAL auto-configuration and
 * a database, but without {@code TestJobs}, and without the test schema that creates the lock
 * table for the H2 contexts of the other tests.
 */
@SpringBootTest(classes = BatchTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:web-context-a02;DB_CLOSE_DELAY=-1",
                "spring.sql.init.mode=never"})
class BatchWebContextTest {

    @Autowired
    ApplicationContext context;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("the context is a servlet web context: nothing of the kit's turned it into a batch one")
    void itIsAWebContext() {
        assertThat(context).isInstanceOf(WebApplicationContext.class);
        assertThat(context.getEnvironment().getProperty("spring.main.web-application-type")).isNull();
    }

    @Test
    @DisplayName("without a Job bean the kit's auto-configuration contributes nothing")
    void noBatchBeans() {
        assertThat(context.getBeanNamesForType(BatchProperties.class)).isEmpty();
        assertThat(context.getBeanNamesForType(BatchOperator.class)).isEmpty();
        assertThat(context.getBeanNamesForType(AdharSteps.class)).isEmpty();
        assertThat(context.getBeanNamesForType(SchedulerLock.class)).isEmpty();
        assertThat(context.getBeanNamesForType(BatchSchemaMigrator.class)).isEmpty();
    }

    @Test
    @DisplayName("no adhar_scheduler_lock table (nor any other kit table) is created")
    void noKitTables() {
        List<String> tables = jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_schema = 'PUBLIC'", String.class)
                .stream().map(name -> name.toLowerCase(Locale.ROOT)).toList();

        assertThat(tables).doesNotContain("adhar_scheduler_lock", "adhar_batch_execution_lease",
                "adhar_batch_dead_letter");
    }
}
