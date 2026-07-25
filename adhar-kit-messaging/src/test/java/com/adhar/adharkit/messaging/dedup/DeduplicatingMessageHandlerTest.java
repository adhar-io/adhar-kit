package com.adhar.adharkit.messaging.dedup;

import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.dedup.DeduplicatingMessageHandler;
import com.adhar.kit.messaging.dedup.ProcessedMessageStore;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DeduplicatingMessageHandler}.
 */
class DeduplicatingMessageHandlerTest {

    private ProcessedMessageStore store;
    private MessageHandler.MessageContext context;
    private AtomicInteger delegateCalls;
    private MessageHandler<String> delegate;

    @BeforeEach
    void setUp() {
        store = mock(ProcessedMessageStore.class);
        context = mock(MessageHandler.MessageContext.class);
        when(context.getDestination()).thenReturn("order-events");
        delegateCalls = new AtomicInteger();
        delegate = (payload, headers, ctx) -> {
            delegateCalls.incrementAndGet();
            return true;
        };
    }

    @Test
    void invokesDelegateWhenMessageIsNew() {
        when(store.markIfNotProcessed("abc-123")).thenReturn(true);
        DeduplicatingMessageHandler<String> handler = new DeduplicatingMessageHandler<>(delegate, store);

        Map<String, Object> headers = Map.of("ce-id", "abc-123");
        boolean result = handler.handle("payload", headers, context);

        assertTrue(result);
        assertEquals(1, delegateCalls.get());
    }

    @Test
    void skipsDelegateWhenMessageIsADuplicate() {
        when(store.markIfNotProcessed("abc-123")).thenReturn(false);
        DeduplicatingMessageHandler<String> handler = new DeduplicatingMessageHandler<>(delegate, store);

        Map<String, Object> headers = Map.of("ce-id", "abc-123");
        boolean result = handler.handle("payload", headers, context);

        assertTrue(result, "a duplicate must still be reported as handled so it is acknowledged");
        assertEquals(0, delegateCalls.get());
    }

    @Test
    void prefersCeIdHeaderOverMessageIdHeader() {
        when(store.markIfNotProcessed("ce-value")).thenReturn(true);
        DeduplicatingMessageHandler<String> handler = new DeduplicatingMessageHandler<>(delegate, store);

        Map<String, Object> headers = new HashMap<>();
        headers.put("ce-id", "ce-value");
        headers.put("messageId", "message-id-value");

        handler.handle("payload", headers, context);

        verify(store).markIfNotProcessed("ce-value");
        verify(store, never()).markIfNotProcessed("message-id-value");
    }

    @Test
    void fallsBackToMessageIdHeaderWhenNoCeId() {
        when(store.markIfNotProcessed("message-id-value")).thenReturn(true);
        DeduplicatingMessageHandler<String> handler = new DeduplicatingMessageHandler<>(delegate, store);

        Map<String, Object> headers = Map.of("messageId", "message-id-value");
        handler.handle("payload", headers, context);

        verify(store).markIfNotProcessed("message-id-value");
    }

    @Test
    void fallsBackToContextMessageIdWhenNoHeaders() {
        when(context.getMessageId()).thenReturn("context-message-id");
        when(store.markIfNotProcessed("context-message-id")).thenReturn(true);
        DeduplicatingMessageHandler<String> handler = new DeduplicatingMessageHandler<>(delegate, store);

        handler.handle("payload", Map.of(), context);

        verify(store).markIfNotProcessed("context-message-id");
    }

    @Test
    void invokesDelegateWhenNoKeyCanBeResolved() {
        when(context.getMessageId()).thenReturn(null);
        DeduplicatingMessageHandler<String> handler = new DeduplicatingMessageHandler<>(delegate, store);

        boolean result = handler.handle("payload", Map.of(), context);

        assertTrue(result);
        assertEquals(1, delegateCalls.get());
        verify(store, never()).markIfNotProcessed(eq((String) null));
    }

    @Test
    void recordsDuplicateMetricOnSkip() {
        when(store.markIfNotProcessed("abc-123")).thenReturn(false);
        MessagingMetrics metrics = mock(MessagingMetrics.class);
        DeduplicatingMessageHandler<String> handler = new DeduplicatingMessageHandler<>(delegate, store, metrics);

        handler.handle("payload", Map.of("ce-id", "abc-123"), context);

        verify(metrics).recordDuplicate("order-events");
    }

    @Test
    void doesNotRecordDuplicateMetricOnNewMessage() {
        when(store.markIfNotProcessed("abc-123")).thenReturn(true);
        MessagingMetrics metrics = mock(MessagingMetrics.class);
        DeduplicatingMessageHandler<String> handler = new DeduplicatingMessageHandler<>(delegate, store, metrics);

        handler.handle("payload", Map.of("ce-id", "abc-123"), context);

        verify(metrics, never()).recordDuplicate(eq("order-events"));
    }
}
