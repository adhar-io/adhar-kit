package com.adhar.kit.batch.runtime;

import org.springframework.jdbc.core.JdbcOperations;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * {@link ExecutionLease} in the kit's {@code ADHAR_BATCH_EXECUTION_LEASE} table (migration
 * {@code V6_0_1}), on the JobRepository's database. Every time is the database's
 * {@code CURRENT_TIMESTAMP}.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
public class JdbcExecutionLease implements ExecutionLease {

    private final JdbcOperations jdbc;
    private final String table;

    /**
     * @param jdbc  the JobRepository's database
     * @param table the table, schema-qualified on PostgreSQL
     */
    public JdbcExecutionLease(JdbcOperations jdbc, String table) {
        this.jdbc = jdbc;
        this.table = table;
    }

    @Override
    public void acquire(long executionId, Owner owner) {
        jdbc.update("INSERT INTO " + table + " (JOB_EXECUTION_ID, OWNER_POD, OWNER_UID, NODE, ACQUIRED_AT, "
                + "HEARTBEAT_AT) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                executionId, owner.pod(), owner.uid(), owner.node());
    }

    @Override
    public boolean heartbeat(long executionId, Owner owner, Instant lastCommit) {
        return jdbc.update("UPDATE " + table + " SET HEARTBEAT_AT = CURRENT_TIMESTAMP, LAST_COMMIT_AT = "
                + "COALESCE(?, LAST_COMMIT_AT) WHERE JOB_EXECUTION_ID = ? AND OWNER_POD = ? "
                + "AND COALESCE(OWNER_UID, '') = ?", lastCommit == null ? null : lastCommit.atOffset(ZoneOffset.UTC),
                executionId, owner.pod(), owner.uid() == null ? "" : owner.uid()) == 1;
    }

    @Override
    public void release(long executionId) {
        jdbc.update("DELETE FROM " + table + " WHERE JOB_EXECUTION_ID = ?", executionId);
    }

    @Override
    public Optional<Lease> find(long executionId) {
        List<Lease> leases = jdbc.query("SELECT JOB_EXECUTION_ID, OWNER_POD, OWNER_UID, NODE, ACQUIRED_AT, "
                + "HEARTBEAT_AT, LAST_COMMIT_AT, CURRENT_TIMESTAMP AS DB_NOW FROM " + table
                + " WHERE JOB_EXECUTION_ID = ?", (rs, row) -> lease(rs), executionId);
        return leases.stream().findFirst();
    }

    private static Lease lease(ResultSet rs) throws SQLException {
        Instant heartbeat = instant(rs, "HEARTBEAT_AT");
        Instant now = instant(rs, "DB_NOW");
        return new Lease(rs.getLong("JOB_EXECUTION_ID"),
                new Owner(rs.getString("OWNER_POD"), nullToEmpty(rs.getString("OWNER_UID")),
                        nullToEmpty(rs.getString("NODE"))),
                instant(rs, "ACQUIRED_AT"), heartbeat, instant(rs, "LAST_COMMIT_AT"),
                Duration.between(heartbeat, now).isNegative() ? Duration.ZERO : Duration.between(heartbeat, now));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
