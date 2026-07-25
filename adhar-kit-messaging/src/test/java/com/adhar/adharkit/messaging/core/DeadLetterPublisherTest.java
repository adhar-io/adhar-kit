package com.adhar.adharkit.messaging.core;

import com.adhar.kit.messaging.core.DeadLetterPublisher;
import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DeadLetterPublisher}. Uses a mocked {@link MessagePublisher} so no
 * broker or Docker is involved.
 */
class DeadLetterPublisherTest {

    private AdharMessagingProperties.CommonProperties.DlqProperties dlqProperties;
    private MessagePublisher messagePublisher;
    private MessageHandler.MessageContext context;

    @BeforeEach
    void setUp() {
        dlqProperties = new AdharMessagingProperties.CommonProperties.DlqProperties();
        dlqProperties.setEnabled(true);
        dlqProperties.setTopicSuffix(".dlq");

        messagePublisher = mock(MessagePublisher.class);
        context = mock(MessageHandler.MessageContext.class);
        when(context.getDestination()).thenReturn("order-events");
    }

    @Test
    void publishesToSuffixedDestinationWithFailureHeaders() {
        when(messagePublisher.publish(anyString(), anyMap(), any())).thenReturn(true);
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties);

        RuntimeException error = new RuntimeException("processing failed");
        Map<String, Object> originalHeaders = new HashMap<>();
        originalHeaders.put("ce-id", "abc-123");

        boolean result = dlq.publish(context, "payload", originalHeaders, error, 3);

        assertTrue(result);
        verify(messagePublisher).publish(eq("order-events.dlq"), any(Map.class), eq("payload"));
    }

    @Test
    void includesOriginalHeadersPlusFailureMetadata() {
        when(messagePublisher.publish(anyString(), anyMap(), any())).thenAnswer(invocation -> {
            Map<String, Object> headers = invocation.getArgument(1);
            assertEquals("true", headers.get(DeadLetterPublisher.HEADER_DLQ_FLAG));
            assertEquals("order-events", headers.get(DeadLetterPublisher.HEADER_ORIGINAL_DESTINATION));
            assertEquals("3", headers.get(DeadLetterPublisher.HEADER_ATTEMPTS));
            assertEquals(IllegalStateException.class.getName(), headers.get(DeadLetterPublisher.HEADER_EXCEPTION_CLASS));
            assertEquals("boom", headers.get(DeadLetterPublisher.HEADER_EXCEPTION_MESSAGE));
            assertEquals("value1", headers.get("original-header"));
            return true;
        });
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties);

        Map<String, Object> originalHeaders = new HashMap<>();
        originalHeaders.put("original-header", "value1");

        assertTrue(dlq.publish(context, "payload", originalHeaders, new IllegalStateException("boom"), 3));
    }

    @Test
    void returnsFalseWhenDlqDisabled() {
        dlqProperties.setEnabled(false);
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties);

        boolean result = dlq.publish(context, "payload", Map.of(), new RuntimeException("boom"), 1);

        assertFalse(result);
        verify(messagePublisher, never()).publish(anyString(), anyMap(), any());
    }

    @Test
    void guardsAgainstReDlqingAnAlreadyDlqdMessage() {
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties);

        Map<String, Object> headers = new HashMap<>();
        headers.put(DeadLetterPublisher.HEADER_DLQ_FLAG, "true");

        boolean result = dlq.publish(context, "payload", headers, new RuntimeException("boom"), 1);

        assertFalse(result);
        verify(messagePublisher, never()).publish(anyString(), anyMap(), any());
    }

    @Test
    void guardsAgainstPublishingToASecondDlqSuffix() {
        when(context.getDestination()).thenReturn("order-events.dlq");
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties);

        boolean result = dlq.publish(context, "payload", Map.of(), new RuntimeException("boom"), 1);

        assertFalse(result);
        verify(messagePublisher, never()).publish(anyString(), anyMap(), any());
    }

    @Test
    void handlesNullContextGracefully() {
        when(messagePublisher.publish(anyString(), anyMap(), any())).thenReturn(true);
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties);

        boolean result = dlq.publish(null, "payload", Map.of(), new RuntimeException("boom"), 1);

        assertTrue(result);
        verify(messagePublisher).publish(eq("unknown.dlq"), any(Map.class), eq("payload"));
    }

    @Test
    void handlesNullExceptionGracefully() {
        when(messagePublisher.publish(anyString(), anyMap(), any())).thenReturn(true);
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties);

        assertTrue(dlq.publish(context, "payload", Map.of(), null, 1));
    }

    @Test
    void returnsFalseAndLogsWhenPublishFails() {
        when(messagePublisher.publish(anyString(), anyMap(), any())).thenReturn(false);
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties);

        boolean result = dlq.publish(context, "payload", Map.of(), new RuntimeException("boom"), 2);

        assertFalse(result);
    }

    @Test
    void recordsDlqMetricOnSuccessfulPublish() {
        when(messagePublisher.publish(anyString(), anyMap(), any())).thenReturn(true);
        MessagingMetrics metrics = mock(MessagingMetrics.class);
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties, metrics);

        dlq.publish(context, "payload", Map.of(), new RuntimeException("boom"), 1);

        verify(metrics).recordDlq("order-events");
    }

    @Test
    void doesNotRecordDlqMetricWhenPublishFails() {
        when(messagePublisher.publish(anyString(), anyMap(), any())).thenReturn(false);
        MessagingMetrics metrics = mock(MessagingMetrics.class);
        DeadLetterPublisher dlq = new DeadLetterPublisher(messagePublisher, dlqProperties, metrics);

        dlq.publish(context, "payload", Map.of(), new RuntimeException("boom"), 1);

        verify(metrics, never()).recordDlq(any());
    }
}
