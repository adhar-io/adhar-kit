package com.adhar.kit.messaging.outbox;

import com.adhar.kit.messaging.properties.AdharMessagingProperties.OutboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Relational {@link OutboxStore} backed by a plain {@link JdbcTemplate}.
 * <p>
 * This implementation gives the transactional outbox its durability guarantee: when the
 * {@link #save(OutboxEntry)} runs inside the same database transaction as the business
 * change (e.g. under a Spring {@code @Transactional} boundary sharing the same
 * {@code DataSource}), the outbox row is committed atomically with that change. A
 * separate {@link OutboxRelay} pass then publishes the persisted rows to the broker.
 * <p>
 * The module deliberately does <b>not</b> depend on {@code adhar-kit-persistence}; only a
 * {@code javax.sql.DataSource} / {@code spring-jdbc} is required, and the store is
 * auto-configured only when both are present (see {@code MessagingAutoConfiguration}).
 */
public class JdbcOutboxStore implements OutboxStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcOutboxStore.class);

    /**
     * Guards the configurable table name against SQL injection: the name is interpolated
     * into DDL/DML (it cannot be a bind parameter), so it must be a plain SQL identifier.
     */
    private static final Pattern SAFE_TABLE_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final String tableName;

    private final RowMapper<OutboxEntry> rowMapper = (rs, rowNum) -> {
        OutboxEntry entry = new OutboxEntry();
        entry.setId(rs.getString("id"));
        entry.setDestination(rs.getString("destination"));
        entry.setRoutingKey(rs.getString("routing_key"));
        entry.setPayload(rs.getString("payload"));
        entry.setPayloadType(rs.getString("payload_type"));
        entry.setStatus(OutboxStatus.valueOf(rs.getString("status")));
        entry.setAttempts(rs.getInt("attempts"));
        entry.setCreatedAt(toInstant(rs.getTimestamp("created_at")));
        entry.setLastAttemptAt(toInstant(rs.getTimestamp("last_attempt_at")));
        entry.setLastError(rs.getString("last_error"));
        return entry;
    };

    /**
     * Creates a JDBC-backed store, optionally creating its table at startup.
     *
     * @param jdbcTemplate the template used for all persistence operations
     * @param properties   outbox configuration (table name, schema initialization flag)
     */
    public JdbcOutboxStore(JdbcTemplate jdbcTemplate, OutboxProperties properties) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate");
        Objects.requireNonNull(properties, "properties");
        this.tableName = validateTableName(properties.getTableName());
        if (properties.isInitializeSchema()) {
            initializeSchema();
        }
    }

    private static String validateTableName(String tableName) {
        if (tableName == null || !SAFE_TABLE_NAME.matcher(tableName).matches()) {
            throw new IllegalArgumentException("Illegal outbox table name: " + tableName
                    + " (must match " + SAFE_TABLE_NAME.pattern() + ")");
        }
        return tableName;
    }

    /**
     * Creates the outbox table if it does not already exist. Uses portable column types
     * that work across the common relational databases (H2, PostgreSQL, MySQL, ...).
     */
    public void initializeSchema() {
        String ddl = "CREATE TABLE IF NOT EXISTS " + tableName + " ("
                + "id VARCHAR(64) PRIMARY KEY, "
                + "destination VARCHAR(512) NOT NULL, "
                + "routing_key VARCHAR(512), "
                + "payload CLOB, "
                + "payload_type VARCHAR(512), "
                + "status VARCHAR(32) NOT NULL, "
                + "attempts INT NOT NULL, "
                + "created_at TIMESTAMP NOT NULL, "
                + "last_attempt_at TIMESTAMP, "
                + "last_error VARCHAR(2048))";
        jdbcTemplate.execute(ddl);
        log.debug("Ensured outbox table {} exists", tableName);
    }

    @Override
    public void save(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.getId(), "entry.id");
        jdbcTemplate.update("INSERT INTO " + tableName + " (id, destination, routing_key, payload, payload_type, "
                        + "status, attempts, created_at, last_attempt_at, last_error) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                entry.getId(), entry.getDestination(), entry.getRoutingKey(), entry.getPayload(),
                entry.getPayloadType(), entry.getStatus().name(), entry.getAttempts(),
                toTimestamp(entry.getCreatedAt()), toTimestamp(entry.getLastAttemptAt()), entry.getLastError());
    }

    @Override
    public List<OutboxEntry> fetchPending(int limit) {
        int effectiveLimit = Math.max(0, limit);
        if (effectiveLimit == 0) {
            return List.of();
        }
        return jdbcTemplate.query("SELECT * FROM " + tableName + " WHERE status IN (?, ?) "
                        + "ORDER BY created_at ASC, id ASC LIMIT ?",
                rowMapper, OutboxStatus.PENDING.name(), OutboxStatus.FAILED.name(), effectiveLimit);
    }

    @Override
    public void update(OutboxEntry entry) {
        Objects.requireNonNull(entry, "entry");
        jdbcTemplate.update("UPDATE " + tableName + " SET status = ?, attempts = ?, last_attempt_at = ?, "
                        + "last_error = ? WHERE id = ?",
                entry.getStatus().name(), entry.getAttempts(), toTimestamp(entry.getLastAttemptAt()),
                entry.getLastError(), entry.getId());
    }

    @Override
    public Optional<OutboxEntry> findById(String id) {
        List<OutboxEntry> results = jdbcTemplate.query("SELECT * FROM " + tableName + " WHERE id = ?", rowMapper, id);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public long countByStatus(OutboxStatus status) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName + " WHERE status = ?",
                Long.class, status.name());
        return count != null ? count : 0L;
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp != null ? timestamp.toInstant() : null;
    }
}
