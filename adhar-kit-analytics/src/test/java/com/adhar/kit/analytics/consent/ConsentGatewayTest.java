package com.adhar.kit.analytics.consent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConsentGateway Tests")
class ConsentGatewayTest {

    @Test
    @DisplayName("blocks sends for opted-out distinct ids")
    void blocksOptedOutIds() {
        ConsentGateway gateway = new ConsentGateway(new InMemoryConsentStore(java.util.List.of("blocked-user")));

        assertFalse(gateway.isAllowed("blocked-user"));
        assertTrue(gateway.isAllowed("allowed-user"));
    }

    @Test
    @DisplayName("optOut/optIn via gateway update the underlying store")
    void optOutAndOptInDelegateToStore() {
        ConsentGateway gateway = new ConsentGateway(new InMemoryConsentStore());

        assertTrue(gateway.isAllowed("user1"));
        gateway.optOut("user1");
        assertFalse(gateway.isAllowed("user1"));
        gateway.optIn("user1");
        assertTrue(gateway.isAllowed("user1"));
    }

    @Test
    @DisplayName("null distinct id is always allowed (callers validate presence separately)")
    void nullDistinctIdAllowed() {
        ConsentGateway gateway = new ConsentGateway(new InMemoryConsentStore());
        assertTrue(gateway.isAllowed(null));
    }

    @Test
    @DisplayName("a null store defaults to an in-memory store")
    void nullStoreDefaultsToInMemory() {
        ConsentGateway gateway = new ConsentGateway(null);
        assertNotNull(gateway.store());
        assertTrue(gateway.isAllowed("anyone"));
    }
}
