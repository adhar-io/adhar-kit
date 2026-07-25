package com.adhar.adharkit.messaging.core;

import com.adhar.kit.messaging.core.DeadLetterPublisher;
import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.core.RetryingMessageHandler;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RetryingMessageHandler}. All tests use a no-op {@link RetryingMessageHandler.Sleeper}
 * so the retry/backoff loop runs instantly - no Docker, no real broker, no real sleeping.
 */
class RetryingMessageHandlerTest {

    private AdharMessagingProperties.CommonProperties.RetryProperties retryProperties;
    private MessageHandler.MessageContext context;
    private List<Long> sleeps;
    private RetryingMessageHandler.Sleeper recordingSleeper;

    @BeforeEach
    void setUp() {
        retryProperties = new AdharMessagingProperties.CommonProperties.RetryProperties();
        retryProperties.setMaxAttempts(3);
        retryProperties.setInitialDelayMs(10);
        retryProperties.setBackoffMultiplier(2.0);
        retryProperties.setMaxDelayMs(1000);

        context = mock(MessageHandler.MessageContext.class);
        when(context.getDestination()).thenReturn("order-events");

        sleeps = new java.util.ArrayList<>();
        recordingSleeper = millis -> sleeps.add(millis);
    }

    @Test
    void succeedsOnFirstAttemptWithoutRetrying() {
        AtomicInteger calls = new AtomicInteger();
        MessageHandler<String> delegate = (payload, headers, ctx) -> {
            calls.incrementAndGet();
            return true;
        };

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties, recordingSleeper);

        boolean result = handler.handle("payload", Map.of(), context);

        assertTrue(result);
        assertEquals(1, calls.get());
        assertTrue(sleeps.isEmpty(), "no sleep should occur when the first attempt succeeds");
    }

    @Test
    void retriesOnFalseThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        MessageHandler<String> delegate = (payload, headers, ctx) -> calls.incrementAndGet() >= 2;

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties, recordingSleeper);

        boolean result = handler.handle("payload", Map.of(), context);

        assertTrue(result);
        assertEquals(2, calls.get());
        assertEquals(List.of(10L), sleeps, "exactly one backoff sleep between the two attempts");
    }

    @Test
    void retriesOnExceptionThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        MessageHandler<String> delegate = (payload, headers, ctx) -> {
            if (calls.incrementAndGet() == 1) {
                throw new RuntimeException("boom");
            }
            return true;
        };

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties, recordingSleeper);

        assertTrue(handler.handle("payload", Map.of(), context));
        assertEquals(2, calls.get());
    }

    @Test
    void exhaustsAttemptsAndAppliesExponentialBackoff() {
        MessageHandler<String> delegate = (payload, headers, ctx) -> false;

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties, recordingSleeper);

        boolean result = handler.handle("payload", Map.of(), context);

        assertFalse(result);
        // maxAttempts=3 => 2 inter-attempt sleeps, growing by the 2.0 multiplier: 10, then 20.
        assertEquals(List.of(10L, 20L), sleeps);
    }

    @Test
    void capsBackoffAtMaxDelay() {
        retryProperties.setMaxAttempts(4);
        retryProperties.setInitialDelayMs(100);
        retryProperties.setBackoffMultiplier(10.0);
        retryProperties.setMaxDelayMs(150);

        MessageHandler<String> delegate = (payload, headers, ctx) -> false;
        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties, recordingSleeper);

        handler.handle("payload", Map.of(), context);

        // Attempt delays would be 100, 1000, 10000 uncapped; each must be capped at 150.
        assertEquals(List.of(100L, 150L, 150L), sleeps);
    }

    @Test
    void publishesToDeadLetterAfterExhaustingRetriesWhenDlqConfigured() {
        MessageHandler<String> delegate = (payload, headers, ctx) -> {
            throw new IllegalStateException("always fails");
        };
        DeadLetterPublisher dlq = mock(DeadLetterPublisher.class);

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(
                delegate, retryProperties, recordingSleeper, dlq, null);

        Map<String, Object> headers = new HashMap<>();
        boolean result = handler.handle("payload", headers, context);

        assertFalse(result);
        verify(dlq).publish(eq(context), eq("payload"), eq(headers), any(IllegalStateException.class), eq(3));
    }

    @Test
    void doesNotPublishToDeadLetterWhenNoneConfigured() {
        MessageHandler<String> delegate = (payload, headers, ctx) -> false;
        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties, recordingSleeper);

        assertFalse(handler.handle("payload", Map.of(), context));
        // No DeadLetterPublisher was passed in - nothing to verify beyond "no exception".
    }

    @Test
    void recordsRetryAndFailureMetrics() {
        MessageHandler<String> delegate = (payload, headers, ctx) -> false;
        MessagingMetrics metrics = mock(MessagingMetrics.class);

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(
                delegate, retryProperties, recordingSleeper, null, metrics);

        handler.handle("payload", Map.of(), context);

        // maxAttempts=3 => 2 retries recorded (not on the final attempt).
        verify(metrics, times(2)).recordRetry("order-events");
        verify(metrics).recordConsumeFailure("order-events");
    }

    @Test
    void doesNotRecordFailureMetricOnEventualSuccess() {
        AtomicInteger calls = new AtomicInteger();
        MessageHandler<String> delegate = (payload, headers, ctx) -> calls.incrementAndGet() >= 2;
        MessagingMetrics metrics = mock(MessagingMetrics.class);

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(
                delegate, retryProperties, recordingSleeper, null, metrics);

        assertTrue(handler.handle("payload", Map.of(), context));

        verify(metrics, never()).recordConsumeFailure(any());
    }

    @Test
    void stopsRetryingWhenSleepIsInterrupted() throws InterruptedException {
        MessageHandler<String> delegate = (payload, headers, ctx) -> false;
        RetryingMessageHandler.Sleeper interruptingSleeper = millis -> {
            throw new InterruptedException("interrupted");
        };

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties, interruptingSleeper);

        try {
            boolean result = handler.handle("payload", Map.of(), context);
            assertFalse(result);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            // Clear the interrupted flag so it doesn't leak into other tests.
            Thread.interrupted();
        }
    }

    @Test
    void defaultConstructorUsesRealThreadSleep() {
        // Exercise the public single-arg-properties constructor (production code path)
        // with an immediate success so no actual sleeping occurs.
        MessageHandler<String> delegate = (payload, headers, ctx) -> true;
        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties);

        assertTrue(handler.handle("payload", Map.of(), context));
    }

    @Test
    void treatsNonPositiveMaxAttemptsAsOne() {
        retryProperties.setMaxAttempts(0);
        AtomicInteger calls = new AtomicInteger();
        MessageHandler<String> delegate = (payload, headers, ctx) -> {
            calls.incrementAndGet();
            return false;
        };

        RetryingMessageHandler<String> handler = new RetryingMessageHandler<>(delegate, retryProperties, recordingSleeper);

        assertFalse(handler.handle("payload", Map.of(), context));
        assertEquals(1, calls.get(), "at least one attempt must always be made");
    }
}
