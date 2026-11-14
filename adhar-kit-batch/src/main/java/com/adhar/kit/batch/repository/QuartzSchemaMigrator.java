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
import java.util.Set;

/**
 * The kit's SECOND migration stream (design 0025 §5.20.3): Quartz's eleven job store tables in
 * the batch schema, for the engine {@code quartz} of the embedded scheduler, with a Flyway history
 * of its own ({@code <schema>.adhar_batch_quartz_schema_history}).
 *
 * <p>A separate stream, not more migrations in the main one, for three reasons: a service on the
 * engine {@code simple} gets no Quartz tables; Quartz's DDL versions follow Quartz, not Spring
 * Batch ({@code V2_5_2} copies Quartz 2.5.2's {@code tables_postgres.sql}); and the main stream's
 * versions can land before or after it without Flyway refusing an out-of-order migration.</p>
 *
 * <p>It runs only with {@code adhar.batch.scheduler.engine=quartz}, after the main stream (the bean
 * depends on {@link BatchSchemaMigrator}) and before the Quartz scheduler exists: it is a database
 * initializer ({@link QuartzSchemaMigratorDetector}), and Spring Boot's Quartz scheduler depends on
 * database initialization. With {@code adhar.batch.repository.migrate=false} it verifies that the
 * eleven tables exist instead, as the main stream does for its own.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class QuartzSchemaMigrator implements InitializingBean {

    /** Flyway history table of the Quartz stream. */
    public static final String HISTORY_TABLE = "adhar_batch_quartz_schema_history";

    /** Classpath location of the Quartz stream's migrations. */
    public static final String LOCATION = "classpath:com/adhar/kit/batch/db/postgresql/quartz";

    /** The Quartz tables' fixed prefix inside the batch schema (§5.20.3 rules 1-2). */
    public static final String TABLE_PREFIX = "QRTZ_";

    /** Quartz 2.5.2's job store tables, without the prefix. */
    public static final List<String> TABLES = List.of("JOB_DETAILS", "TRIGGERS", "SIMPLE_TRIGGERS", "CRON_TRIGGERS",
            "SIMPROP_TRIGGERS", "BLOB_TRIGGERS", "CALENDARS", "PAUSED_TRIGGER_GRPS", "FIRED_TRIGGERS",
            "SCHEDULER_STATE", "LOCKS");

    private final DataSource dataSource;
    private final BatchRepositoryLayout layout;
    private final boolean migrate;

    /**
     * @param dataSource the JobRepository's DataSource, which the Quartz store shares
     * @param layout     where the repository lives
     * @param migrate    {@code adhar.batch.repository.migrate}: run the migrations, or only verify
     * @param mainStream the main stream's migrator, which must have run first
     */
    public QuartzSchemaMigrator(DataSource dataSource, BatchRepositoryLayout layout, boolean migrate,
                                BatchSchemaMigrator mainStream) {
        this.dataSource = dataSource;
        this.layout = layout;
        this.migrate = migrate;
    }

    /**
     * @param layout where the repository lives
     * @return Quartz's {@code org.quartz.jobStore.tablePrefix}: {@code <schema>.QRTZ_}
     */
    public static String tablePrefix(BatchRepositoryLayout layout) {
        return layout.schema() + "." + TABLE_PREFIX;
    }

    @Override
    public void afterPropertiesSet() {
        if (!layout.managed()) {
            throw new BatchSchemaMigrationException("adhar.batch.scheduler.engine=quartz needs the JobRepository on "
                    + "PostgreSQL, where the kit manages the batch schema; it is on " + layout.databaseProduct()
                    + ". Use the engine simple, or a PostgreSQL JobRepository.");
        }
        if (migrate) {
            migrate();
        }
        verify();
    }

    /**
     * @return Flyway's result
     * @throws BatchSchemaMigrationException when a migration fails or the history does not match
     */
    MigrateResult migrate() {
        Flyway flyway = Flyway.configure(QuartzSchemaMigrator.class.getClassLoader())
                .dataSource(dataSource)
                .schemas(layout.schema())
                .defaultSchema(layout.schema())
                .createSchemas(true)
                .table(HISTORY_TABLE)
                // The schema is never empty: the main stream ran first. Its own history starts at a
                // baseline below every Quartz version, so V2_5_2 is applied, and a schema that already
                // holds Quartz tables from elsewhere fails on them instead of being adopted.
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .baselineDescription("adhar-kit quartz stream")
                .locations(LOCATION)
                .resourceProvider(new DirectoryMigrations(LOCATION, QuartzSchemaMigrator.class.getClassLoader()))
                .failOnMissingLocations(true)
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load();
        try {
            MigrateResult result = flyway.migrate();
            log.info("Quartz job store in schema '{}' at version {} ({} migration(s) applied)", layout.schema(),
                    result.targetSchemaVersion != null ? result.targetSchemaVersion : result.initialSchemaVersion,
                    result.migrationsExecuted);
            return result;
        } catch (FlywayException ex) {
            throw new BatchSchemaMigrationException("Migrating the Quartz job store in schema '" + layout.schema()
                    + "' failed: " + ex.getMessage() + ". Quartz tables created outside the kit are not adopted: "
                    + "the kit's store lives in its own migration history (" + HISTORY_TABLE + ").", ex);
        }
    }

    /**
     * @throws BatchSchemaMigrationException naming the Quartz tables the schema is missing
     */
    void verify() {
        Set<String> tables = new HashSet<>();
        try {
            new JdbcTemplate(dataSource).queryForList(
                    "select table_name from information_schema.tables where table_schema = ?", String.class,
                    layout.schema()).forEach(table -> tables.add(table.toLowerCase(Locale.ROOT)));
        } catch (DataAccessException ex) {
            throw new BatchSchemaMigrationException("Could not inspect the Quartz job store in schema '"
                    + layout.schema() + "': " + ex.getMessage(), ex);
        }
        List<String> missing = new ArrayList<>();
        for (String table : TABLES) {
            if (!tables.contains((TABLE_PREFIX + table).toLowerCase(Locale.ROOT))) {
                missing.add(TABLE_PREFIX + table);
            }
        }
        if (!missing.isEmpty()) {
            throw new BatchSchemaMigrationException("The Quartz job store in schema '" + layout.schema()
                    + "' is missing " + missing + ". "
                    + (migrate ? "The kit's Quartz migrations ran but these tables are absent."
                    : "adhar.batch.repository.migrate is false: create them with the kit's Quartz migrations ("
                            + LOCATION + ") or set it to true."));
        }
        log.debug("Quartz job store in schema '{}' verified", layout.schema());
    }
}
