package com.adhar.adharkit.messaging.outbox;

import com.adhar.kit.messaging.outbox.InMemoryOutboxStore;
import com.adhar.kit.messaging.outbox.OutboxEntry;
import com.adhar.kit.messaging.outbox.OutboxStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link InMemoryOutboxStore}.
 */
class InMemoryOutboxStoreTest {

    private final InMemoryOutboxStore store = new InMemoryOutboxStore();

    @Test
    void saveAndFindById() {
        OutboxEntry entry = OutboxEntry.pending("orders", null, "{}", "java.lang.String");
        store.save(entry);

        assertTrue(store.findById(entry.getId()).isPresent());
        assertEquals(1, store.countByStatus(OutboxStatus.PENDING));
    }

    @Test
    void saveRejectsNullEntryOrId() {
        assertThrows(NullPointerException.class, () -> store.save(null));
        assertThrows(NullPointerException.class, () -> store.save(new OutboxEntry()));
    }

    @Test
    void fetchPendingReturnsPendingAndFailedOldestFirst() {
        OutboxEntry older = OutboxEntry.pending("orders", null, "1", "java.lang.String");
        older.setCreatedAt(Instant.now().minusSeconds(10));
        OutboxEntry newer = OutboxEntry.pending("orders", null, "2", "java.lang.String");
        newer.setCreatedAt(Instant.now());
        OutboxEntry failed = OutboxEntry.pending("orders", null, "3", "java.lang.String");
        failed.setCreatedAt(Instant.now().minusSeconds(5));
        failed.setStatus(OutboxStatus.FAILED);
        OutboxEntry published = OutboxEntry.pending("orders", null, "4", "java.lang.String");
        published.setStatus(OutboxStatus.PUBLISHED);

        store.save(older);
        store.save(newer);
        store.save(failed);
        store.save(published);

        List<OutboxEntry> pending = store.fetchPending(10);
        assertEquals(3, pending.size(), "PUBLISHED entries must be excluded");
        assertEquals("1", pending.get(0).getPayload());
        assertEquals("3", pending.get(1).getPayload());
        assertEquals("2", pending.get(2).getPayload());
    }

    @Test
    void fetchPendingHonoursLimit() {
        for (int i = 0; i < 5; i++) {
            store.save(OutboxEntry.pending("orders", null, String.valueOf(i), "java.lang.String"));
        }
        assertEquals(2, store.fetchPending(2).size());
        assertEquals(0, store.fetchPending(0).size());
    }

    @Test
    void updatePersistsMutatedState() {
        OutboxEntry entry = OutboxEntry.pending("orders", null, "{}", "java.lang.String");
        store.save(entry);

        entry.setStatus(OutboxStatus.PUBLISHED);
        store.update(entry);

        assertEquals(OutboxStatus.PUBLISHED, store.findById(entry.getId()).orElseThrow().getStatus());
        assertEquals(0, store.countByStatus(OutboxStatus.PENDING));
        assertEquals(1, store.countByStatus(OutboxStatus.PUBLISHED));
    }
}
