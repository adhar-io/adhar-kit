package com.adhar.kit.messaging.dapr;

import com.adhar.kit.dapr.DaprFacade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DaprMessagePublisher} using a mocked {@link DaprFacade}.
 */
class DaprMessagePublisherTest {

    private DaprFacade daprFacade;
    private DaprMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        daprFacade = mock(DaprFacade.class);
        publisher = new DaprMessagePublisher(daprFacade, "pubsub", "fallback-topic");
    }

    @Test
    void constructorRejectsNullFacadeAndBlankPubsub() {
        assertThrows(IllegalArgumentException.class, () -> new DaprMessagePublisher(null, "pubsub"));
        assertThrows(IllegalArgumentException.class, () -> new DaprMessagePublisher(daprFacade, " "));
    }

    @Test
    void publishDelegatesToDaprWithEmptyMetadata() {
        assertTrue(publisher.publish("orders", "payload"));

        verify(daprFacade).publishEvent(eq("pubsub"), eq("orders"), eq("payload"), eq(Map.of()));
    }

    @Test
    void publishWithoutDestinationUsesDefaultTopic() {
        assertTrue(publisher.publish("payload"));

        verify(daprFacade).publishEvent(eq("pubsub"), eq("fallback-topic"), eq("payload"), anyMap());
    }

    @Test
    void routingKeyBecomesPartitionKeyMetadata() {
        assertTrue(publisher.publish("orders", "customer-1", "payload"));

        verify(daprFacade).publishEvent(eq("pubsub"), eq("orders"), eq("payload"),
                eq(Map.of("partitionKey", "customer-1")));
    }

    @Test
    void headersAreStringifiedAndCloudEventHeadersTranslated() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("ce-type", "order.created");
        headers.put("retry-count", 3);
        headers.put("nullValued", null);

        assertTrue(publisher.publish("orders", "key-9", headers, "payload"));

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass((Class) Map.class);
        verify(daprFacade).publishEvent(eq("pubsub"), eq("orders"), eq("payload"), metadata.capture());
        assertEquals("order.created", metadata.getValue().get("cloudevent.type"));
        assertEquals("3", metadata.getValue().get("retry-count"));
        assertEquals("key-9", metadata.getValue().get("partitionKey"));
        assertFalse(metadata.getValue().containsKey("nullValued"));
    }

    @Test
    void publishReturnsFalseWhenDaprFails() {
        doThrow(new RuntimeException("sidecar down"))
                .when(daprFacade).publishEvent(anyString(), anyString(), any(), anyMap());

        assertFalse(publisher.publish("orders", "payload"));
    }

    @Test
    void publishAsyncCompletesWithPublishResult() {
        assertTrue(publisher.publishAsync("orders", "payload").join());

        verify(daprFacade).publishEvent(eq("pubsub"), eq("orders"), eq("payload"), anyMap());
    }
}
