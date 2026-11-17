package com.adhar.kit.batch.repository;

import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.sql.init.dependency.DatabaseInitializerDetector;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link QuartzSchemaMigrator}, the kit's second migration stream (design 0025 §5.20.3),
 * without PostgreSQL. Flyway really runs against H2 in PostgreSQL mode, which accepts Quartz
 * 2.5.2's {@code tables_postgres.sql} as it is, so the stream's own rules are exercised for real:
 * its history table beside the main stream's, the baseline in a schema that is never empty, the
 * refusal to adopt tables created elsewhere, and the verification. That the DDL is Quartz's own on
 * PostgreSQL 18 is {@code QuartzSchemaParityTestContainerTest}'s.
 */
class QuartzSchemaMigratorTest {

    private static final String SCHEMA = "batch";

    private final String url = "jdbc:h2:mem:quartz-migrator-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    private final DataSource h2 = new DriverManagerDataSource(url, "sa", "");
    private final JdbcTemplate jdbc = new JdbcTemplate(h2);
    private final BatchRepositoryLayout layout = new BatchRepositoryLayout(true, "PostgreSQL", SCHEMA, "BATCH_");

    @AfterEach
    void tearDown() {
        jdbc.execute("SHUTDOWN");
    }

    /** The schema as the main stream leaves it: never empty when the Quartz stream runs. */
    private void mainStreamRan() {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        jdbc.execute("CREATE TABLE " + SCHEMA + ".batch_job_instance (ID BIGINT)");
        jdbc.execute("CREATE TABLE " + SCHEMA + "." + BatchSchemaMigrator.HISTORY_TABLE + " (ID BIGINT)");
    }

    private QuartzSchemaMigrator migrator(boolean migrate) {
        return new QuartzSchemaMigrator(h2, layout, migrate, null);
    }

    private List<String> tables() {
        return jdbc.queryForList("select table_name from information_schema.tables where table_schema = ? "
                + "order by table_name", String.class, SCHEMA);
    }

    @Test
    @DisplayName("the stream creates Quartz's eleven tables in the batch schema, with a history of its own")
    void createsTheJobStore() {
        mainStreamRan();

        migrator(true).afterPropertiesSet();

        assertThat(tables()).contains("qrtz_job_details", "qrtz_triggers", "qrtz_simple_triggers", "qrtz_cron_triggers",
                "qrtz_simprop_triggers", "qrtz_blob_triggers", "qrtz_calendars", "qrtz_paused_trigger_grps",
                "qrtz_fired_triggers", "qrtz_scheduler_state", "qrtz_locks", QuartzSchemaMigrator.HISTORY_TABLE);
        assertThat(tables().stream().filter(table -> table.startsWith("qrtz_"))).hasSize(QuartzSchemaMigrator.TABLES.size());
        assertThat(jdbc.queryForList("select \"version\" from " + SCHEMA + "." + QuartzSchemaMigrator.HISTORY_TABLE
                + " where \"version\" is not null order by \"installed_rank\"", String.class))
                .as("a baseline below every Quartz version, then Quartz 2.5.2's DDL").containsExactly("1", "2.5.2");
        assertThat(jdbc.queryForObject("select count(*) from " + SCHEMA + ".batch_job_instance", Integer.class))
                .as("the main stream's tables are left alone").isZero();
    }

    @Test
    @DisplayName("a second start applies nothing and verifies the same schema")
    void isIdempotent() {
        mainStreamRan();
        migrator(true).afterPropertiesSet();

        MigrateResult again = migrator(true).migrate();

        assertThat(again.migrationsExecuted).isZero();
        assertThatCode(() -> migrator(true).afterPropertiesSet()).doesNotThrowAnyException();
        assertThatCode(() -> migrator(false).afterPropertiesSet()).as("migrate=false verifies only")
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("with migrate=false a schema without the job store names the missing tables and how to create them")
    void namesWhatIsMissing() {
        mainStreamRan();
        jdbc.execute("CREATE TABLE " + SCHEMA + ".qrtz_locks (ID BIGINT)");

        assertThatThrownBy(() -> migrator(false).afterPropertiesSet())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("The Quartz job store in schema 'batch' is missing [QRTZ_JOB_DETAILS, QRTZ_TRIGGERS")
                .hasMessageContaining("QRTZ_SCHEDULER_STATE]")
                .hasMessageContaining("adhar.batch.repository.migrate is false")
                .hasMessageContaining(QuartzSchemaMigrator.LOCATION);
        assertThat(tables()).as("nothing was created").doesNotContain("qrtz_triggers", QuartzSchemaMigrator.HISTORY_TABLE);
    }

    @Test
    @DisplayName("a table that went missing after the migrations ran is named as that")
    void namesATableThatWentMissing() {
        mainStreamRan();
        migrator(true).afterPropertiesSet();
        jdbc.execute("DROP TABLE " + SCHEMA + ".qrtz_paused_trigger_grps");

        assertThatThrownBy(() -> migrator(true).afterPropertiesSet())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("is missing [QRTZ_PAUSED_TRIGGER_GRPS]")
                .hasMessageContaining("The kit's Quartz migrations ran but these tables are absent");
    }

    @Test
    @DisplayName("Quartz tables in another schema do not count")
    void anotherSchemaDoesNotCount() {
        mainStreamRan();
        jdbc.execute("CREATE SCHEMA other");
        for (String table : QuartzSchemaMigrator.TABLES) {
            jdbc.execute("CREATE TABLE other." + QuartzSchemaMigrator.TABLE_PREFIX + table + " (ID BIGINT)");
        }

        assertThatThrownBy(() -> migrator(false).afterPropertiesSet()).isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("QRTZ_LOCKS");
    }

    @Test
    @DisplayName("Quartz tables created outside the kit are not adopted: the migration fails on them")
    void doesNotAdoptAForeignJobStore() {
        mainStreamRan();
        jdbc.execute("CREATE TABLE " + SCHEMA + ".qrtz_job_details (ID BIGINT)");

        assertThatThrownBy(() -> migrator(true).afterPropertiesSet())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("Migrating the Quartz job store in schema 'batch' failed")
                .hasMessageContaining("created outside the kit are not adopted")
                .hasMessageContaining(QuartzSchemaMigrator.HISTORY_TABLE)
                .hasCauseInstanceOf(org.flywaydb.core.api.FlywayException.class);
    }

    @Test
    @DisplayName("on a database the kit does not manage the engine quartz is refused, and nothing is touched")
    void refusesAnUnmanagedDatabase() {
        DataSource dataSource = mock(DataSource.class);
        var migrator = new QuartzSchemaMigrator(dataSource, new BatchRepositoryLayout(false, "H2", "", "BATCH_"), true, null);

        assertThatThrownBy(migrator::afterPropertiesSet).isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("adhar.batch.scheduler.engine=quartz needs the JobRepository on PostgreSQL")
                .hasMessageContaining("it is on H2").hasMessageContaining("Use the engine simple");
        verifyNoInteractions(dataSource);
    }

    @Test
    @DisplayName("a schema that cannot be inspected fails with the cause, as a migration failure")
    void cannotInspect() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        var refused = new SQLException("connection refused");
        when(dataSource.getConnection()).thenThrow(refused);

        assertThatThrownBy(() -> new QuartzSchemaMigrator(dataSource, layout, false, null).afterPropertiesSet())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("Could not inspect the Quartz job store in schema 'batch'")
                .hasRootCause(refused);
    }

    @Test
    @DisplayName("Quartz's table prefix is the batch schema's, and Spring Boot learns the migrator initializes a database")
    void prefixAndDetector() {
        assertThat(QuartzSchemaMigrator.tablePrefix(layout)).isEqualTo("batch.QRTZ_");
        assertThat(SpringFactoriesLoader.forDefaultResourceLocation().load(DatabaseInitializerDetector.class))
                .anyMatch(QuartzSchemaMigratorDetector.class::isInstance);
        assertThat(new QuartzSchemaMigratorDetector().getDatabaseInitializerBeanTypes())
                .containsExactly(QuartzSchemaMigrator.class);
    }
}
