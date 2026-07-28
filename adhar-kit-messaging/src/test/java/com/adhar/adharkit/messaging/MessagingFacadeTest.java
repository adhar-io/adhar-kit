package com.adhar.adharkit.messaging;

import com.adhar.kit.messaging.MessagingFacade;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link MessagingFacade} class.
 * <p>
 * The facade is a singleton with default/stub provider behaviour; these tests exercise the
 * subscription registry, the publish/sendAndReceive stubs and the lifecycle accessors without
 * touching any real broker.
 */
class MessagingFacadeTest {

    @Test
    void testGetInstanceReturnsSingleton() {
        MessagingFacade first = MessagingFacade.getInstance();
        MessagingFacade second = MessagingFacade.getInstance();

        assertNotNull(first);
        assertSame(first, second, "getInstance() must always return the same singleton instance");
    }

    @Test
    void testPublishDoesNotThrow() {
        MessagingFacade facade = MessagingFacade.getInstance();

        // The default facade is a stub; publishing must be a no-op that completes normally.
        assertDoesNotThrow(() -> facade.publish("order-events", "payload"));
        assertDoesNotThrow(() -> facade.publish("order-events", "customer-1", "payload"));
    }

    @Test
    void testSubscribeRegistersAndIncrementsCount() {
        MessagingFacade facade = MessagingFacade.getInstance();
        int before = facade.getSubscriptionCount();

        Consumer<String> handler = payload -> { /* no-op */ };
        String subscriptionId = facade.subscribe("order-events", String.class, handler);

        assertNotNull(subscriptionId);
        assertTrue(subscriptionId.startsWith("sub-"), "subscription id should be prefixed with 'sub-'");
        assertEquals(before + 1, facade.getSubscriptionCount());

        // Clean up so the singleton's registry is not polluted for other tests.
        facade.unsubscribe(subscriptionId);
        assertEquals(before, facade.getSubscriptionCount());
    }

    @Test
    void testSubscribeWithGroupRegistersDistinctIds() {
        MessagingFacade facade = MessagingFacade.getInstance();
        int before = facade.getSubscriptionCount();

        Consumer<String> handler = payload -> { /* no-op */ };
        String id1 = facade.subscribe("order-events", "group-a", String.class, handler);
        String id2 = facade.subscribe("order-events", "group-b", String.class, handler);

        assertNotEquals(id1, id2, "each subscription must get a unique id");
        assertEquals(before + 2, facade.getSubscriptionCount());

        facade.unsubscribe(id1);
        facade.unsubscribe(id2);
        assertEquals(before, facade.getSubscriptionCount());
    }

    @Test
    void testUnsubscribeUnknownIdIsNoOp() {
        MessagingFacade facade = MessagingFacade.getInstance();
        int before = facade.getSubscriptionCount();

        // Removing an id that was never registered must not throw and must not change the count.
        assertDoesNotThrow(() -> facade.unsubscribe("does-not-exist"));
        assertEquals(before, facade.getSubscriptionCount());
    }

    @Test
    void testSendAndReceiveWithoutBrokerThrows() {
        MessagingFacade facade = MessagingFacade.getInstance();

        // The default facade has no request-reply client wired, so it must fail loudly
        // rather than silently returning null.
        assertThrows(com.adhar.kit.messaging.exception.MessagingException.class,
                () -> facade.sendAndReceive("order-queries", "request", String.class));
    }

    @Test
    void testIsConnectedReturnsTrue() {
        assertTrue(MessagingFacade.getInstance().isConnected());
    }
}
