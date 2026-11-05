package com.adhar.kit.batch.lock;

import com.adhar.kit.batch.repository.BatchRepositoryLayout;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;

import javax.sql.DataSource;
import java.sql.DatabaseMetaData;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * {@link SchedulerLock} on a table of the JobRepository's database.
 *
 * <p>The table is {@code ADHAR_SCHEDULER_LOCK} (on PostgreSQL in the kit's schema, created by the
 * kit's migration {@code V6_0_1}); this class never creates it. A lock is a row per name: a holder
 * acquires it by inserting the row, or by taking over a row whose {@code LOCKED_UNTIL} has
 * passed, and renews it by moving {@code LOCKED_UNTIL} forward. Every statement is a single
 * conditional write, so two instances can never both succeed. On PostgreSQL acquisition is one
 * {@code INSERT ... ON CONFLICT DO UPDATE ... WHERE}, which never raises (and never logs) a
 * duplicate-key error on a contended fire.</p>
 *
 * <p>Times are written as {@code TIMESTAMP WITH TIME ZONE} from the JVM clock: instances in
 * different time zones agree, and the lease (minutes) dwarfs the clock skew of NTP-synchronised
 * nodes.</p>
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
public class JdbcSchedulerLock implements SchedulerLock {

    /** The table created by the kit's migration. */
    public static final String DEFAULT_TABLE = BatchRepositoryLayout.SCHEDULER_LOCK_TABLE;

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;
    private final String table;
    private final String owner;
    private final Clock clock;
    private volatile Boolean postgres;

    /**
     * @param dataSource the JobRepository's DataSource
     * @param table      the lock table, schema-qualified when it is not in the default schema
     */
    public JdbcSchedulerLock(DataSource dataSource, String table) {
        this(dataSource, table, Clock.systemUTC(), defaultOwner());
    }

    /**
     * @param dataSource the JobRepository's DataSource
     * @param table      the lock table, schema-qualified when it is not in the default schema
     * @param clock      the time source of lock times
     * @param owner      this holder's identity, written to {@code LOCKED_BY}; unique per holder
     */
    public JdbcSchedulerLock(DataSource dataSource, String table, Clock clock, String owner) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.table = table;
        this.clock = clock;
        this.owner = owner;
    }

    /**
     * @return {@code <host>:<pid>:<random>}, readable in the table and unique per holder
     */
    static String defaultOwner() {
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            host = "unknown-host";
        }
        return host + ":" + ProcessHandle.current().pid() + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Override
    public boolean tryLock(String name, Duration lease) {
        OffsetDateTime now = now();
        OffsetDateTime until = now.plus(lease);
        try {
            boolean acquired = isPostgres() ? upsert(name, now, until) : updateOrInsert(name, now, until);
            if (acquired) {
                log.debug("Acquired scheduler lock [{}] until {} (owner={})", name, until, owner);
            }
            return acquired;
        } catch (DataAccessException ex) {
            log.warn("Could not acquire scheduler lock [{}]: {}", name, ex.getMessage());
            return false;
        }
    }

    private boolean upsert(String name, OffsetDateTime now, OffsetDateTime until) {
        return jdbcTemplate.update("INSERT INTO " + table + " AS l (LOCK_NAME, LOCKED_BY, LOCKED_AT, LOCKED_UNTIL) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (LOCK_NAME) DO UPDATE SET LOCKED_BY = EXCLUDED.LOCKED_BY, "
                        + "LOCKED_AT = EXCLUDED.LOCKED_AT, LOCKED_UNTIL = EXCLUDED.LOCKED_UNTIL "
                        + "WHERE l.LOCKED_UNTIL <= EXCLUDED.LOCKED_AT",
                name, owner, now, until) == 1;
    }

    private boolean updateOrInsert(String name, OffsetDateTime now, OffsetDateTime until) {
        int taken = jdbcTemplate.update("UPDATE " + table + " SET LOCKED_BY = ?, LOCKED_AT = ?, LOCKED_UNTIL = ? "
                + "WHERE LOCK_NAME = ? AND LOCKED_UNTIL <= ?", owner, now, until, name, now);
        if (taken == 1) {
            return true;
        }
        try {
            return jdbcTemplate.update("INSERT INTO " + table + " (LOCK_NAME, LOCKED_BY, LOCKED_AT, LOCKED_UNTIL) "
                    + "VALUES (?, ?, ?, ?)", name, owner, now, until) == 1;
        } catch (DuplicateKeyException held) {
            return false;
        }
    }

    @Override
    public boolean renew(String name, Duration lease) {
        boolean renewed = jdbcTemplate.update("UPDATE " + table + " SET LOCKED_UNTIL = ? "
                + "WHERE LOCK_NAME = ? AND LOCKED_BY = ?", now().plus(lease), name, owner) == 1;
        if (!renewed) {
            log.warn("Scheduler lock [{}] is no longer owned by {}", name, owner);
        }
        return renewed;
    }

    @Override
    public void unlock(String name) {
        try {
            // Expire the row rather than delete it: the last holder stays visible.
            jdbcTemplate.update("UPDATE " + table + " SET LOCKED_UNTIL = ? WHERE LOCK_NAME = ? AND LOCKED_BY = ?",
                    now(), name, owner);
            log.debug("Released scheduler lock [{}] (owner={})", name, owner);
        } catch (DataAccessException ex) {
            log.warn("Could not release scheduler lock [{}]; it expires with its lease: {}", name, ex.getMessage());
        }
    }

    @Override
    public void verify() {
        try {
            jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE 1 = 0", Integer.class);
        } catch (DataAccessException ex) {
            throw new IllegalStateException("The scheduler lock table " + table + " is not usable: "
                    + ex.getMessage() + ". On PostgreSQL the kit's migration creates it "
                    + "(adhar.batch.repository.migrate=true). On another database create it with: CREATE TABLE "
                    + table + " (LOCK_NAME VARCHAR(200) NOT NULL PRIMARY KEY, LOCKED_BY VARCHAR(255) NOT NULL, "
                    + "LOCKED_AT TIMESTAMP WITH TIME ZONE NOT NULL, LOCKED_UNTIL TIMESTAMP WITH TIME ZONE NOT NULL)"
                    + ", or set adhar.batch.scheduler.lock=none for a single instance.", ex);
        }
    }

    private boolean isPostgres() {
        Boolean result = postgres;
        if (result == null) {
            try {
                String product = JdbcUtils.extractDatabaseMetaData(dataSource,
                        DatabaseMetaData::getDatabaseProductName);
                result = "PostgreSQL".equalsIgnoreCase(product);
            } catch (MetaDataAccessException ex) {
                // Not cached: the next call asks again. The portable statements work everywhere.
                return false;
            }
            postgres = result;
        }
        return result;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * @return this holder's identity as written to {@code LOCKED_BY}
     */
    public String getOwner() {
        return owner;
    }

    /**
     * @return the lock table this lock writes to
     */
    public String getTable() {
        return table;
    }
}
