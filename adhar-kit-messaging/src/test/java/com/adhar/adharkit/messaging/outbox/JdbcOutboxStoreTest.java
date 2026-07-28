package com.adhar.adharkit.messaging.outbox;

import com.adhar.kit.messaging.outbox.JdbcOutboxStore;
import com.adhar.kit.messaging.outbox.OutboxEntry;
import com.adhar.kit.messaging.outbox.OutboxStatus;
import com.adhar.kit.messaging.properties.AdharMessagingProperties.OutboxProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JdbcOutboxStore} exercised against an in-memory H2 database (no Docker).
 */
class JdbcOutboxStoreTest {

    private DriverManagerDataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private JdbcOutboxStore store;

    @BeforeEach
    void setUp() {
        // Unique DB name per test so tables never leak across tests.
        String url = "jdbc:h2:mem:outbox-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        dataSource = new DriverManagerDataSource(url, "sa", "");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        store = new JdbcOutboxStore(jdbcTemplate, new OutboxProperties());
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("SHUTDOWN");
    }

    @Test
    void schemaIsInitializedAndEntryCanBeSavedAndFetched() {
        OutboxEntry entry = OutboxEntry.pending("orders", "cust-1", "{\"a\":1}", "com.example.Event");
        store.save(entry);

        OutboxEntry loaded = store.findById(entry.getId()).orElseThrow();
        assertEquals("orders", loaded.getDestination());
        assertEquals("cust-1", loaded.getRoutingKey());
        assertEquals("{\"a\":1}", loaded.getPayload());
        assertEquals("com.example.Event", loaded.getPayloadType());
        assertEquals(OutboxStatus.PENDING, loaded.getStatus());
        assertEquals(0, loaded.getAttempts());
        assertTrue(store.findById("missing").isEmpty());
    }

    @Test
    void fetchPendingReturnsPendingAndFailedOldestFirstWithLimit() {
        OutboxEntry older = OutboxEntry.pending("orders", null, "1", "t");
        older.setCreatedAt(Instant.now().minusSeconds(30));
        OutboxEntry failed = OutboxEntry.pending("orders", null, "2", "t");
        failed.setCreatedAt(Instant.now().minusSeconds(20));
        failed.setStatus(OutboxStatus.FAILED);
        OutboxEntry newer = OutboxEntry.pending("orders", null, "3", "t");
        newer.setCreatedAt(Instant.now().minusSeconds(10));
        OutboxEntry published = OutboxEntry.pending("orders", null, "4", "t");
        published.setStatus(OutboxStatus.PUBLISHED);

        store.save(older);
        store.save(failed);
        store.save(newer);
        store.save(published);

        List<OutboxEntry> pending = store.fetchPending(10);
        assertEquals(3, pending.size());
        assertEquals("1", pending.get(0).getPayload());
        assertEquals("2", pending.get(1).getPayload());
        assertEquals("3", pending.get(2).getPayload());

        assertEquals(2, store.fetchPending(2).size());
        assertEquals(0, store.fetchPending(0).size());
    }

    @Test
    void updatePersistsStatusAttemptsAndError() {
        OutboxEntry entry = OutboxEntry.pending("orders", null, "{}", "t");
        store.save(entry);

        entry.setStatus(OutboxStatus.DEAD);
        entry.setAttempts(3);
        entry.setLastAttemptAt(Instant.now());
        entry.setLastError("boom");
        store.update(entry);

        OutboxEntry loaded = store.findById(entry.getId()).orElseThrow();
        assertEquals(OutboxStatus.DEAD, loaded.getStatus());
        assertEquals(3, loaded.getAttempts());
        assertEquals("boom", loaded.getLastError());
    }

    @Test
    void countByStatusCountsCorrectly() {
        store.save(OutboxEntry.pending("orders", null, "1", "t"));
        store.save(OutboxEntry.pending("orders", null, "2", "t"));
        OutboxEntry dead = OutboxEntry.pending("orders", null, "3", "t");
        dead.setStatus(OutboxStatus.DEAD);
        store.save(dead);

        assertEquals(2, store.countByStatus(OutboxStatus.PENDING));
        assertEquals(1, store.countByStatus(OutboxStatus.DEAD));
        assertEquals(0, store.countByStatus(OutboxStatus.PUBLISHED));
    }

    @Test
    void illegalTableNameIsRejected() {
        OutboxProperties bad = new OutboxProperties();
        bad.setTableName("drop table users;--");
        assertThrows(IllegalArgumentException.class, () -> new JdbcOutboxStore(jdbcTemplate, bad));
    }

    @Test
    void customTableNameIsHonoured() {
        OutboxProperties props = new OutboxProperties();
        props.setTableName("custom_outbox");
        JdbcOutboxStore custom = new JdbcOutboxStore(jdbcTemplate, props);
        OutboxEntry entry = OutboxEntry.pending("orders", null, "{}", "t");
        custom.save(entry);

        assertEquals(1L, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM custom_outbox", Long.class));
    }
}
