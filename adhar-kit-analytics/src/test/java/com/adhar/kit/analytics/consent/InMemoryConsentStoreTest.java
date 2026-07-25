package com.adhar.kit.analytics.consent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("InMemoryConsentStore Tests")
class InMemoryConsentStoreTest {

    @Test
    @DisplayName("seeded IDs are opted out from construction")
    void seededIdsAreOptedOut() {
        InMemoryConsentStore store = new InMemoryConsentStore(List.of("user1", "user2"));

        assertTrue(store.isOptedOut("user1"));
        assertTrue(store.isOptedOut("user2"));
        assertFalse(store.isOptedOut("user3"));
        assertEquals(2, store.size());
    }

    @Test
    @DisplayName("optOut/optIn toggle state")
    void optOutAndOptIn() {
        InMemoryConsentStore store = new InMemoryConsentStore();

        assertFalse(store.isOptedOut("user1"));
        store.optOut("user1");
        assertTrue(store.isOptedOut("user1"));
        store.optIn("user1");
        assertFalse(store.isOptedOut("user1"));
    }

    @Test
    @DisplayName("null distinct ids and null seed collection are handled safely")
    void nullsAreHandledSafely() {
        InMemoryConsentStore store = new InMemoryConsentStore(null);

        assertFalse(store.isOptedOut(null));
        assertDoesNotThrow(() -> store.optOut(null));
        assertDoesNotThrow(() -> store.optIn(null));
        assertEquals(0, store.size());
    }
}
