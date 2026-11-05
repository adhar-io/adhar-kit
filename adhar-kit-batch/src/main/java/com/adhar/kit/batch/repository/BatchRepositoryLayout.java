package com.adhar.kit.batch.repository;

import com.adhar.kit.batch.config.BatchProperties;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Locale;

/**
 * Where the JobRepository and the kit's own tables live.
 *
 * <p>On PostgreSQL the kit manages the schema ({@code managed == true}): Spring Batch's tables
 * are {@code <schema>.<tablePrefix>JOB_INSTANCE} and so on, and the kit's tables are
 * {@code <schema>.ADHAR_*}. On any other database the application's own setup decides (Spring
 * Boot's {@code spring.batch.jdbc.*}); the kit's tables are then expected unqualified, in the
 * connection's default schema.</p>
 *
 * @param managed         whether the kit migrates and verifies the schema (PostgreSQL)
 * @param databaseProduct the database product name reported by the JDBC driver
 * @param schema          the configured schema ({@code adhar.batch.repository.schema})
 * @param tablePrefix     the configured prefix ({@code adhar.batch.repository.table-prefix})
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public record BatchRepositoryLayout(boolean managed, String databaseProduct, String schema, String tablePrefix) {

    /** The kit's scheduler lock table (embedded mode). */
    public static final String SCHEDULER_LOCK_TABLE = "ADHAR_SCHEDULER_LOCK";

    /** The execution lease table of the Kubernetes runner. */
    public static final String EXECUTION_LEASE_TABLE = "ADHAR_BATCH_EXECUTION_LEASE";

    /** The dead-letter table of fault-tolerant steps. */
    public static final String DEAD_LETTER_TABLE = "ADHAR_BATCH_DEAD_LETTER";

    /** Spring Batch 6.0's tables, without their prefix. */
    static final List<String> SPRING_BATCH_TABLES = List.of("JOB_INSTANCE", "JOB_EXECUTION",
            "JOB_EXECUTION_PARAMS", "JOB_EXECUTION_CONTEXT", "STEP_EXECUTION", "STEP_EXECUTION_CONTEXT");

    /** Spring Batch 6.0's sequences, without their prefix. */
    static final List<String> SPRING_BATCH_SEQUENCES = List.of("JOB_INSTANCE_SEQ", "JOB_EXECUTION_SEQ",
            "STEP_EXECUTION_SEQ");

    /** The kit's own tables. */
    static final List<String> KIT_TABLES = List.of(SCHEDULER_LOCK_TABLE, EXECUTION_LEASE_TABLE, DEAD_LETTER_TABLE);

    private static final String POSTGRESQL = "PostgreSQL";

    /**
     * Resolves the layout for the JobRepository's DataSource.
     *
     * @param dataSource the DataSource the JobRepository uses
     * @param repository the {@code adhar.batch.repository.*} properties
     * @return the layout
     * @throws MetaDataAccessException when the database cannot be reached to read its product
     *                                 name (a connectivity failure, not a configuration one)
     */
    public static BatchRepositoryLayout detect(DataSource dataSource, BatchProperties.Repository repository)
            throws MetaDataAccessException {
        String product = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
        return new BatchRepositoryLayout(POSTGRESQL.equalsIgnoreCase(product), product,
                repository.getSchema(), repository.getTablePrefix());
    }

    /**
     * The value of {@code spring.batch.jdbc.table-prefix} for a managed layout.
     *
     * @return {@code <schema>.<tablePrefix>}
     */
    public String qualifiedTablePrefix() {
        return schema + "." + tablePrefix;
    }

    /**
     * The name to use in SQL for one of the kit's own tables.
     *
     * @param table the table name, for example {@link #SCHEDULER_LOCK_TABLE}
     * @return {@code <schema>.<table>} when managed, else the bare table name
     */
    public String kitTable(String table) {
        return managed ? schema + "." + table : table;
    }

    /**
     * Whether {@code spring.batch.jdbc.table-prefix} as configured agrees with this layout.
     *
     * @param configured the configured Spring Batch prefix, {@code null} when unset
     * @return true when unset or equal (ignoring case, as PostgreSQL folds unquoted names)
     */
    public boolean acceptsSpringBatchPrefix(String configured) {
        return configured == null || configured.toLowerCase(Locale.ROOT)
                .equals(qualifiedTablePrefix().toLowerCase(Locale.ROOT));
    }
}
