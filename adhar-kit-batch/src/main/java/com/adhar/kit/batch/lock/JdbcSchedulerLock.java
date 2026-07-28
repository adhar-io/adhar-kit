package com.adhar.kit.batch.lock;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * JDBC-backed {@link SchedulerLock} providing multi-instance cron safety using a
 * single database lock table, without requiring an external locking library.
 *
 * <p>Acquisition is atomic: an instance either inserts a fresh lock row or
 * updates an existing row whose {@code locked_until} has already passed. Because
 * both operations are guarded by the database (primary key + conditional
 * update), only one instance can hold a given lock at a time. Locks carry an
 * expiry so a crashed holder never blocks the schedule permanently.</p>
 *
 * <p>The lock table is created automatically on construction if it does not
 * already exist (portable DDL suitable for H2, PostgreSQL, and MySQL).</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class JdbcSchedulerLock implements SchedulerLock {

    /** Default lock table name. */
    public static final String DEFAULT_TABLE = "adhar_scheduler_lock";

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;
    private final String instanceId;

    /**
     * Creates a lock backed by the given data source using the default table name.
     *
     * @param dataSource the data source
     */
    public JdbcSchedulerLock(DataSource dataSource) {
        this(dataSource, DEFAULT_TABLE);
    }

    /**
     * Creates a lock backed by the given data source and table name.
     *
     * @param dataSource the data source
     * @param tableName  the lock table name
     */
    public JdbcSchedulerLock(DataSource dataSource, String tableName) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.tableName = tableName;
        this.instanceId = UUID.randomUUID().toString();
        initTable();
    }

    private void initTable() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "lock_name VARCHAR(255) PRIMARY KEY, "
                + "locked_by VARCHAR(255) NOT NULL, "
                + "locked_at TIMESTAMP NOT NULL, "
                + "locked_until TIMESTAMP NOT NULL)");
        log.debug("Scheduler lock table [{}] ready (instance={})", tableName, instanceId);
    }

    @Override
    public boolean tryLock(String name, Duration ttl) {
        var now = Instant.now();
        var until = now.plus(ttl);
        var nowTs = Timestamp.from(now);
        var untilTs = Timestamp.from(until);

        // Fast path: no row yet -> try to insert.
        try {
            int inserted = jdbcTemplate.update(
                    "INSERT INTO " + tableName + " (lock_name, locked_by, locked_at, locked_until) VALUES (?, ?, ?, ?)",
                    name, instanceId, nowTs, untilTs);
            if (inserted > 0) {
                log.debug("Acquired scheduler lock [{}] via insert (instance={})", name, instanceId);
                return true;
            }
        } catch (DuplicateKeyException ex) {
            // Row already exists -> fall through to the expiry-based update.
        } catch (DataAccessException ex) {
            log.warn("Failed to acquire scheduler lock [{}] via insert: {}", name, ex.getMessage());
        }

        // Contended path: take over the lock only if the existing one has expired.
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE " + tableName + " SET locked_by = ?, locked_at = ?, locked_until = ? "
                            + "WHERE lock_name = ? AND locked_until <= ?",
                    instanceId, nowTs, untilTs, name, nowTs);
            if (updated > 0) {
                log.debug("Acquired scheduler lock [{}] via expiry takeover (instance={})", name, instanceId);
                return true;
            }
        } catch (DataAccessException ex) {
            log.warn("Failed to acquire scheduler lock [{}] via update: {}", name, ex.getMessage());
        }
        return false;
    }

    @Override
    public void unlock(String name) {
        try {
            // Expire the lock (rather than delete) so history/ownership is visible,
            // and only if this instance still owns it.
            jdbcTemplate.update(
                    "UPDATE " + tableName + " SET locked_until = ? WHERE lock_name = ? AND locked_by = ?",
                    Timestamp.from(Instant.now()), name, instanceId);
            log.debug("Released scheduler lock [{}] (instance={})", name, instanceId);
        } catch (DataAccessException ex) {
            log.warn("Failed to release scheduler lock [{}]: {}", name, ex.getMessage());
        }
    }

    /**
     * @return the unique id identifying this lock instance (lock owner)
     */
    public String getInstanceId() {
        return instanceId;
    }
}
