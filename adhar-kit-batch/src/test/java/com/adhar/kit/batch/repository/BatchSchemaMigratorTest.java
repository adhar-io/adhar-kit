package com.adhar.kit.batch.repository;

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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link BatchSchemaMigrator} without PostgreSQL. {@code verify()} only reads
 * {@code information_schema}, which H2 implements, so a managed layout is verified for real here.
 * Flyway also really runs against H2 in PostgreSQL mode: {@code V6_0_0} applies, and
 * {@code V6_0_1} stops at its partial index, which H2 has no syntax for, so a real migration
 * failure is exercised too. A migration that runs to its end needs PostgreSQL:
 * {@code BatchAutoConfigurationTestContainerTest} covers that.
 */
class BatchSchemaMigratorTest {

    private static final String SCHEMA = "batch";

    private final String url = "jdbc:h2:mem:migrator-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1";
    private final DataSource h2 = new DriverManagerDataSource(url, "sa", "");
    private final JdbcTemplate jdbc = new JdbcTemplate(h2);

    @AfterEach
    void tearDown() {
        jdbc.execute("SHUTDOWN");
    }

    private BatchRepositoryLayout layout(String prefix) {
        return new BatchRepositoryLayout(true, "PostgreSQL", SCHEMA, prefix);
    }

    private void createObjects(String prefix) {
        jdbc.execute("CREATE SCHEMA IF NOT EXISTS " + SCHEMA);
        for (String table : BatchRepositoryLayout.SPRING_BATCH_TABLES) {
            jdbc.execute("CREATE TABLE " + SCHEMA + "." + prefix + table + " (ID BIGINT)");
        }
        for (String sequence : BatchRepositoryLayout.SPRING_BATCH_SEQUENCES) {
            jdbc.execute("CREATE SEQUENCE " + SCHEMA + "." + prefix + sequence);
        }
        for (String table : BatchRepositoryLayout.KIT_TABLES) {
            jdbc.execute("CREATE TABLE " + SCHEMA + "." + table + " (ID BIGINT)");
        }
    }

    // ------------------------------------------------------------------
    // Not managed
    // ------------------------------------------------------------------

    @Test
    @DisplayName("on a database the kit does not manage it touches nothing")
    void unmanagedTouchesNothing() {
        DataSource dataSource = mock(DataSource.class);
        var migrator = new BatchSchemaMigrator(dataSource, new BatchRepositoryLayout(false, "H2", SCHEMA, "BATCH_"), true);

        migrator.afterPropertiesSet();

        verifyNoInteractions(dataSource);
        assertThat(migrator.getLayout().databaseProduct()).isEqualTo("H2");
    }

    // ------------------------------------------------------------------
    // Verify
    // ------------------------------------------------------------------

    @Test
    @DisplayName("with migrate=false an existing, complete schema is verified and left as it is")
    void verifiesAnExistingSchema() {
        createObjects("BATCH_");

        new BatchSchemaMigrator(h2, layout("BATCH_"), false).afterPropertiesSet();

        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? "
                + "and lower(table_name) = ?", Integer.class, SCHEMA, BatchSchemaMigrator.HISTORY_TABLE)).isZero();
    }

    @Test
    @DisplayName("with migrate=false a missing schema names everything that is absent and how to fix it")
    void namesWhatIsMissing() {
        var migrator = new BatchSchemaMigrator(h2, layout("BATCH_"), false);

        assertThatThrownBy(migrator::afterPropertiesSet)
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("schema 'batch' is missing")
                .hasMessageContaining("BATCH_JOB_INSTANCE")
                .hasMessageContaining("BATCH_STEP_EXECUTION_CONTEXT")
                .hasMessageContaining("BATCH_JOB_INSTANCE_SEQ")
                .hasMessageContaining("ADHAR_SCHEDULER_LOCK")
                .hasMessageContaining("adhar.batch.repository.migrate is false")
                .hasMessageContaining(BatchSchemaMigrator.LOCATION);
    }

    @Test
    @DisplayName("one missing table, sequence or kit table is enough, and it is the only one named")
    void namesOnlyWhatIsMissing() {
        createObjects("BATCH_");
        jdbc.execute("DROP TABLE batch.BATCH_JOB_EXECUTION_PARAMS");
        jdbc.execute("DROP SEQUENCE batch.BATCH_STEP_EXECUTION_SEQ");
        jdbc.execute("DROP TABLE batch.ADHAR_BATCH_DEAD_LETTER");

        assertThatThrownBy(() -> new BatchSchemaMigrator(h2, layout("BATCH_"), false).verify())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("[BATCH_JOB_EXECUTION_PARAMS, BATCH_STEP_EXECUTION_SEQ, ADHAR_BATCH_DEAD_LETTER]");
    }

    @Test
    @DisplayName("the configured prefix decides which tables are looked for; the kit's tables have none")
    void verifiesWithThePrefix() {
        createObjects("RUN_");

        new BatchSchemaMigrator(h2, layout("RUN_"), false).verify();

        assertThatThrownBy(() -> new BatchSchemaMigrator(h2, layout("BATCH_"), true).verify())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("BATCH_JOB_INSTANCE")
                .hasMessageNotContaining("ADHAR_SCHEDULER_LOCK")
                .hasMessageContaining("table-prefix changed after the schema was created");
    }

    @Test
    @DisplayName("objects in another schema do not count")
    void anotherSchemaDoesNotCount() {
        createObjects("BATCH_");

        var migrator = new BatchSchemaMigrator(h2, new BatchRepositoryLayout(true, "PostgreSQL", "elsewhere", "BATCH_"),
                false);

        assertThatThrownBy(migrator::verify)
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("schema 'elsewhere' is missing");
    }

    @Test
    @DisplayName("a schema that cannot be inspected fails with the cause, as a migration failure")
    void cannotInspect() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        var refused = new SQLException("connection refused");
        when(dataSource.getConnection()).thenThrow(refused);

        assertThatThrownBy(() -> new BatchSchemaMigrator(dataSource, layout("BATCH_"), false).verify())
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("Could not inspect the JobRepository schema 'batch'")
                .hasRootCause(refused);
    }

    // ------------------------------------------------------------------
    // Migrate (Flyway really runs; H2 cannot finish V6_0_1)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("migrate creates the schema, applies the prefix placeholder and keeps its own history table")
    void migrationRunsAgainstTheConfiguredSchemaAndPrefix() {
        var migrator = new BatchSchemaMigrator(h2, layout("RUN_"), true);

        assertThatThrownBy(migrator::migrate).isInstanceOf(BatchSchemaMigrationException.class);

        List<String> tables = jdbc.queryForList("select lower(table_name) from information_schema.tables "
                + "where table_schema = ?", String.class, SCHEMA);
        assertThat(tables).contains("run_job_instance", "run_job_execution", "run_step_execution",
                BatchSchemaMigrator.HISTORY_TABLE);
        assertThat(tables).doesNotContain("batch_job_instance", "flyway_schema_history");
        assertThat(jdbc.queryForList("select \"version\" from batch.adhar_batch_schema_history where \"success\" "
                + "and \"version\" is not null", String.class)).contains("6.0.0");
    }

    @Test
    @DisplayName("a failed migration becomes exit code 2 with the cause and the way out")
    void failedMigrationSaysWhatToDo() {
        var migrator = new BatchSchemaMigrator(h2, layout("BATCH_"), true);

        assertThatThrownBy(migrator::afterPropertiesSet)
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("Migrating the JobRepository schema 'batch' failed")
                .hasMessageContaining("adhar.batch.repository.migrate=false")
                .hasCauseInstanceOf(org.flywaydb.core.api.FlywayException.class)
                .satisfies(failure -> assertThat(((BatchRepositoryException) failure).getExitCode()).isEqualTo(2));
    }

    @Test
    @DisplayName("a schema already holding tables created outside the kit is not adopted")
    void doesNotAdoptAForeignSchema() {
        createObjects("BATCH_");
        var migrator = new BatchSchemaMigrator(h2, layout("BATCH_"), true);

        assertThatThrownBy(migrator::migrate)
                .isInstanceOf(BatchSchemaMigrationException.class)
                .hasMessageContaining("is not adopted");

        assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? "
                + "and lower(table_name) = ?", Integer.class, SCHEMA, BatchSchemaMigrator.HISTORY_TABLE)).isZero();
    }

    @Test
    @DisplayName("Spring Boot learns the migrator is a database initializer, so the JobRepository waits for it")
    void registeredAsADatabaseInitializer() {
        assertThat(SpringFactoriesLoader.forDefaultResourceLocation().load(DatabaseInitializerDetector.class))
                .hasAtLeastOneElementOfType(BatchSchemaMigratorDetector.class);
        assertThat(new DetectorProbe().types()).containsExactly(BatchSchemaMigrator.class);
    }

    private static final class DetectorProbe extends BatchSchemaMigratorDetector {
        private java.util.Set<Class<?>> types() {
            return getDatabaseInitializerBeanTypes();
        }
    }
}
