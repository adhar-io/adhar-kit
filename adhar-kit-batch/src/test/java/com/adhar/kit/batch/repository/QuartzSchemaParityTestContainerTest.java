package com.adhar.kit.batch.repository;

import com.adhar.kit.batch.support.BatchTestApplication;
import com.adhar.kit.batch.support.PostgresDatabases;
import com.adhar.kit.batch.support.TestJobs;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.Scheduler;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * KIT-Q.2 (design 0025 §5.20.3, AQ.7): the kit's second migration stream creates EXACTLY the job
 * store Quartz expects. The migrated {@code batch.QRTZ_*} tables are compared, column by column,
 * constraint by constraint and index by index, with the {@code tables_postgres.sql} inside the Quartz
 * jar on the test classpath, run into a schema of its own: a Quartz upgrade that changes the DDL
 * fails the build until a new migration is written.
 */
@Testcontainers(disabledWithoutDocker = true)
class QuartzSchemaParityTestContainerTest {

    private static final String JAR_DDL = "org/quartz/impl/jdbcjobstore/tables_postgres.sql";

    private static BatchRepositoryLayout layout() {
        return new BatchRepositoryLayout(true, "PostgreSQL", "batch", "BATCH_");
    }

    @Test
    @DisplayName("the migrated batch.QRTZ_* tables equal the Quartz jar's tables_postgres.sql (AQ.7)")
    void theMigratedTablesEqualTheJarsDdl() throws Exception {
        DataSource dataSource = PostgresDatabases.dataSource(PostgresDatabases.createDatabase("qparity"));
        BatchSchemaMigrator main = new BatchSchemaMigrator(dataSource, layout(), true);
        main.afterPropertiesSet();
        new QuartzSchemaMigrator(dataSource, layout(), true, main).afterPropertiesSet();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA reference");
            statement.execute("SET search_path TO reference");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(JAR_DDL, Scheduler.class.getClassLoader()));
        }
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(tables(jdbc, "batch")).as("the Quartz tables").containsExactlyElementsOf(tables(jdbc, "reference"));
        assertThat(columns(jdbc, "batch")).as("every column: type, length, precision, nullability, default, order")
                .containsExactlyEntriesOf(columns(jdbc, "reference"));
        assertThat(constraints(jdbc, "batch")).as("primary and foreign keys")
                .containsExactlyEntriesOf(constraints(jdbc, "reference"));
        assertThat(indexes(jdbc, "batch")).as("indexes").containsExactlyEntriesOf(indexes(jdbc, "reference"));
        assertThat(tables(jdbc, "batch")).hasSize(QuartzSchemaMigrator.TABLES.size());
    }

    @Test
    @DisplayName("the Quartz stream keeps its own history, at 2.5.2; the main stream's history never names it")
    void theQuartzStreamHasItsOwnHistory() throws Exception {
        DataSource dataSource = PostgresDatabases.dataSource(PostgresDatabases.createDatabase("qhistory"));
        BatchSchemaMigrator main = new BatchSchemaMigrator(dataSource, layout(), true);
        main.afterPropertiesSet();
        new QuartzSchemaMigrator(dataSource, layout(), true, main).afterPropertiesSet();
        // A second start applies nothing and verifies.
        new QuartzSchemaMigrator(dataSource, layout(), true, main).afterPropertiesSet();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForList("SELECT version FROM batch." + QuartzSchemaMigrator.HISTORY_TABLE
                + " WHERE success ORDER BY installed_rank", String.class)).containsExactly("1", "2.5.2");
        assertThat(jdbc.queryForList("SELECT version FROM batch." + BatchSchemaMigrator.HISTORY_TABLE
                + " WHERE version IS NOT NULL ORDER BY installed_rank", String.class))
                .containsExactly("6.0.0", "6.0.1", "6.0.2");
    }

    @Test
    @DisplayName("a service on the engine simple gets no QRTZ_ tables")
    void aSimpleServiceGetsNoQuartzTables() {
        String url = PostgresDatabases.createDatabase("qsimple");
        Map<String, Object> properties = BatchTestApplication.postgres(url);
        properties.put("adhar.batch.scheduler.enabled", "true");
        try (ConfigurableApplicationContext context = BatchTestApplication.builder(TestJobs.class)
                .properties(properties).run()) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);

            assertThat(jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = "
                    + "'batch' AND (table_name LIKE 'qrtz%' OR table_name = ?)", String.class,
                    QuartzSchemaMigrator.HISTORY_TABLE)).isEmpty();
            assertThat(jdbc.queryForList("SELECT script FROM batch." + BatchSchemaMigrator.HISTORY_TABLE, String.class))
                    .noneMatch(script -> script.toLowerCase().contains("quartz"));
            assertThat(context.getBeanNamesForType(QuartzSchemaMigrator.class)).isEmpty();
        }
    }

    @Test
    @DisplayName("adhar.batch.repository.migrate=false verifies the eleven tables instead, naming the missing ones")
    void withoutMigrationTheTablesAreVerified() throws Exception {
        DataSource dataSource = PostgresDatabases.dataSource(PostgresDatabases.createDatabase("qverify"));
        BatchSchemaMigrator main = new BatchSchemaMigrator(dataSource, layout(), true);
        main.afterPropertiesSet();

        assertThatThrownBy(() -> new QuartzSchemaMigrator(dataSource, layout(), false, main).afterPropertiesSet())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("QRTZ_JOB_DETAILS").hasMessageContaining("QRTZ_LOCKS")
                .hasMessageContaining("adhar.batch.repository.migrate is false");
    }

    @Test
    @DisplayName("Quartz tables created outside the kit are refused, not adopted")
    void foreignQuartzTablesAreRefused() throws Exception {
        DataSource dataSource = PostgresDatabases.dataSource(PostgresDatabases.createDatabase("qforeign"));
        BatchSchemaMigrator main = new BatchSchemaMigrator(dataSource, layout(), true);
        main.afterPropertiesSet();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET search_path TO batch");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(JAR_DDL, Scheduler.class.getClassLoader()));
        }

        assertThatThrownBy(() -> new QuartzSchemaMigrator(dataSource, layout(), true, main).afterPropertiesSet())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("not adopted");
    }

    private static List<String> tables(JdbcTemplate jdbc, String schema) {
        return jdbc.queryForList("SELECT table_name FROM information_schema.tables WHERE table_schema = ? AND "
                + "table_name LIKE 'qrtz%' ORDER BY table_name", String.class, schema);
    }

    private static Map<String, String> columns(JdbcTemplate jdbc, String schema) {
        Map<String, String> columns = new TreeMap<>();
        jdbc.query("SELECT table_name, column_name, ordinal_position, data_type, character_maximum_length, "
                + "numeric_precision, numeric_scale, is_nullable, column_default FROM information_schema.columns "
                + "WHERE table_schema = ? AND table_name LIKE 'qrtz%'", rs -> {
            columns.put(rs.getString(1) + "." + rs.getString(2), rs.getInt(3) + " " + rs.getString(4) + "("
                    + rs.getObject(5) + "," + rs.getObject(6) + "," + rs.getObject(7) + ") nullable="
                    + rs.getString(8) + " default=" + rs.getString(9));
        }, schema);
        return columns;
    }

    private static Map<String, String> constraints(JdbcTemplate jdbc, String schema) {
        Map<String, String> constraints = new TreeMap<>();
        jdbc.query("SELECT rel.relname, pg_get_constraintdef(con.oid) FROM pg_constraint con "
                + "JOIN pg_class rel ON rel.oid = con.conrelid JOIN pg_namespace ns ON ns.oid = rel.relnamespace "
                + "WHERE ns.nspname = ? AND rel.relname LIKE 'qrtz%'", rs -> {
            constraints.merge(rs.getString(1), rs.getString(2).replace(schema + ".", ""), (a, b) ->
                    a.compareTo(b) < 0 ? a + " | " + b : b + " | " + a);
        }, schema);
        return constraints;
    }

    private static Map<String, String> indexes(JdbcTemplate jdbc, String schema) {
        Map<String, String> indexes = new TreeMap<>();
        jdbc.query("SELECT indexname, indexdef FROM pg_indexes WHERE schemaname = ? AND tablename LIKE 'qrtz%'",
                rs -> {
                    indexes.put(rs.getString(1), rs.getString(2).replace(" " + schema + ".", " "));
                }, schema);
        return indexes;
    }
}
