package com.adhar.kit.batch.repository;

import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Migrates the JobRepository's PostgreSQL schema with the kit's own Flyway history, before
 * anything uses the JobRepository.
 *
 * <p>The schema ({@code adhar.batch.repository.schema}, default {@code batch}) holds Spring
 * Batch's tables and the kit's own. Its history table is
 * {@code <schema>.adhar_batch_schema_history}, so the kit never shares a migration history with
 * the application. This is deliberately not a {@code Flyway} bean: Spring Boot's Flyway
 * auto-configuration backs off when one exists, and the application's own migrations would stop
 * running. Spring Boot orders it before the JobRepository and JPA through its
 * database-initializer detection ({@link BatchSchemaMigratorDetector}).</p>
 *
 * <p>A failed migration, or a schema missing a table afterwards, fails the startup with
 * {@link BatchSchemaMigrationException} (exit code 2): a pod restart cannot fix it. A database
 * that cannot be reached at all is left to fail as a connectivity error (exit code 1), which a
 * retry can fix.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class BatchSchemaMigrator implements InitializingBean {

    /** Flyway history table of the kit's schema. */
    public static final String HISTORY_TABLE = "adhar_batch_schema_history";

    /** Classpath location of the kit's PostgreSQL migrations. */
    public static final String LOCATION = "classpath:com/adhar/kit/batch/db/postgresql";

    /** Placeholder for the Spring Batch table prefix inside the migrations. */
    static final String TABLE_PREFIX_PLACEHOLDER = "tablePrefix";

    private final DataSource dataSource;
    private final BatchRepositoryLayout layout;
    private final boolean migrate;

    /**
     * @param dataSource the JobRepository's DataSource
     * @param layout     where the repository lives
     * @param migrate    {@code adhar.batch.repository.migrate}: run the migrations, or only
     *                   verify an existing schema
     */
    public BatchSchemaMigrator(DataSource dataSource, BatchRepositoryLayout layout, boolean migrate) {
        this.dataSource = dataSource;
        this.layout = layout;
        this.migrate = migrate;
    }

    @Override
    public void afterPropertiesSet() {
        if (!layout.managed()) {
            log.info("JobRepository on {}: its schema is managed by Spring Boot (spring.batch.jdbc.*), not by "
                    + "the kit; the kit manages the schema on PostgreSQL only", layout.databaseProduct());
            return;
        }
        if (migrate) {
            migrate();
        }
        verify();
    }

    /**
     * Applies the kit's pending migrations to the schema, creating it when it does not exist.
     *
     * @return Flyway's result
     * @throws BatchSchemaMigrationException when a migration fails or the history does not match
     */
    MigrateResult migrate() {
        Flyway flyway = Flyway.configure(BatchSchemaMigrator.class.getClassLoader())
                .dataSource(dataSource)
                .schemas(layout.schema())
                .defaultSchema(layout.schema())
                .createSchemas(true)
                .table(HISTORY_TABLE)
                .locations(LOCATION)
                .failOnMissingLocations(true)
                .placeholders(Map.of(TABLE_PREFIX_PLACEHOLDER, layout.tablePrefix()))
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load();
        try {
            MigrateResult result = flyway.migrate();
            log.info("JobRepository schema '{}' at version {} ({} migration(s) applied)", layout.schema(),
                    result.targetSchemaVersion != null ? result.targetSchemaVersion : result.initialSchemaVersion,
                    result.migrationsExecuted);
            return result;
        } catch (FlywayException ex) {
            throw new BatchSchemaMigrationException("Migrating the JobRepository schema '" + layout.schema()
                    + "' failed: " + ex.getMessage() + ". A schema that already holds Spring Batch tables created "
                    + "outside the kit is not adopted: point adhar.batch.repository.schema at an empty schema, or set "
                    + "adhar.batch.repository.migrate=false to use the existing tables as they are.", ex);
        }
    }

    /**
     * Checks that every table and sequence Spring Batch 6.0 and the kit need exists in the
     * schema.
     *
     * @throws BatchSchemaMigrationException naming what is missing
     */
    void verify() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        Set<String> tables;
        Set<String> sequences;
        try {
            tables = lowerCase(jdbc.queryForList(
                    "select table_name from information_schema.tables where table_schema = ?",
                    String.class, layout.schema()));
            sequences = lowerCase(jdbc.queryForList(
                    "select sequence_name from information_schema.sequences where sequence_schema = ?",
                    String.class, layout.schema()));
        } catch (DataAccessException ex) {
            throw new BatchSchemaMigrationException("Could not inspect the JobRepository schema '"
                    + layout.schema() + "': " + ex.getMessage(), ex);
        }
        List<String> missing = new ArrayList<>();
        for (String table : BatchRepositoryLayout.SPRING_BATCH_TABLES) {
            requirePresent(tables, layout.tablePrefix() + table, missing);
        }
        for (String sequence : BatchRepositoryLayout.SPRING_BATCH_SEQUENCES) {
            requirePresent(sequences, layout.tablePrefix() + sequence, missing);
        }
        for (String table : BatchRepositoryLayout.KIT_TABLES) {
            requirePresent(tables, table, missing);
        }
        if (!missing.isEmpty()) {
            throw new BatchSchemaMigrationException("The JobRepository schema '" + layout.schema()
                    + "' is missing " + missing + ". "
                    + (migrate
                    ? "The kit's migrations ran but these objects are absent: was "
                            + "adhar.batch.repository.table-prefix changed after the schema was created? Tables "
                            + "are not renamed."
                    : "adhar.batch.repository.migrate is false: create the schema with the kit's migrations ("
                            + LOCATION + ") or set it to true."));
        }
        log.debug("JobRepository schema '{}' verified (prefix {})", layout.schema(), layout.tablePrefix());
    }

    private static void requirePresent(Set<String> present, String name, List<String> missing) {
        if (!present.contains(name.toLowerCase(Locale.ROOT))) {
            missing.add(name);
        }
    }

    private static Set<String> lowerCase(List<String> names) {
        Set<String> result = new HashSet<>();
        names.forEach(name -> result.add(name.toLowerCase(Locale.ROOT)));
        return result;
    }

    /**
     * @return where the repository lives
     */
    public BatchRepositoryLayout getLayout() {
        return layout;
    }
}
