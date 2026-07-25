package com.adhar.adharkit.messaging.core;

import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.core.SimpleMessageContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SimpleMessageContext}.
 */
class SimpleMessageContextTest {

    @Test
    void exposesConstructorSuppliedValues() {
        long before = System.currentTimeMillis();
        SimpleMessageContext context = new SimpleMessageContext(
                "order-events", "order-events", "group-a", "consumer-1", "group-a", "msg-1");
        long after = System.currentTimeMillis();

        assertEquals("order-events", context.getSource());
        assertEquals("order-events", context.getDestination());
        assertEquals("group-a", context.getRoutingKey());
        assertEquals("consumer-1", context.getConsumerId());
        assertEquals("group-a", context.getConsumerGroup());
        assertEquals("msg-1", context.getMessageId());
        assertTrue(context.getTimestamp() >= before && context.getTimestamp() <= after);
    }

    @Test
    void attributesAreStoredAndRetrieved() {
        SimpleMessageContext context = new SimpleMessageContext("s", "d", "rk", "cid", "grp", "mid");

        assertNull(context.getAttribute("missing"));
        context.setAttribute("key", "value");
        assertEquals("value", context.getAttribute("key"));
    }

    @Test
    void acknowledgeAndRejectAlwaysSucceed() {
        MessageHandler.MessageContext context = new SimpleMessageContext("s", "d", "rk", "cid", "grp", "mid");

        assertTrue(context.acknowledge());
        assertTrue(context.reject(true));
        assertTrue(context.reject(false));
    }
}
