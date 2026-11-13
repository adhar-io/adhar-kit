package com.adhar.kit.batch.config;

import com.adhar.kit.batch.operator.BatchOperator;
import com.adhar.kit.batch.repository.BatchSchemaMigrator;
import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PostgresDatabases;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A0.2 (design 0025 §15) on PostgreSQL 18, the one database the kit migrates: a servlet web
 * service with the kit on its classpath and no job gets no {@code batch} schema and no
 * {@code adhar_scheduler_lock} table. {@link BatchWebContextTest} proves the bean half without
 * Docker; on H2 the kit never migrates, so only this class can see a table appear.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = BatchTestApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class BatchWebContextTestContainerTest {

    private static final String URL = PostgresDatabases.createDatabase("a02");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        BatchTestApplication.postgres(URL).forEach((key, value) -> registry.add(key, () -> value));
    }

    @Autowired
    ApplicationContext context;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a web context without a Job: no batch beans, no batch schema, no adhar_scheduler_lock table")
    void noBatchBeansNoKitSchema() {
        assertThat(context).isInstanceOf(WebApplicationContext.class);
        assertThat(context.getBeanNamesForType(BatchOperator.class)).isEmpty();
        assertThat(context.getBeanNamesForType(BatchSchemaMigrator.class)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.schemata where schema_name = 'batch'", Integer.class))
                .isZero();
        assertThat(jdbcTemplate.queryForList(
                "select table_schema || '.' || table_name from information_schema.tables "
                        + "where table_name like 'adhar\\_%' or table_name like 'batch\\_%'", String.class))
                .isEmpty();
    }
}
