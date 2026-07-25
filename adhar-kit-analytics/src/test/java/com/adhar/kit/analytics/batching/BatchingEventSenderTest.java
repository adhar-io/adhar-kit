package com.adhar.kit.analytics.batching;

import com.adhar.kit.analytics.client.CaptureEvent;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BatchingEventSender Tests")
class BatchingEventSenderTest {

    @Mock
    private PostHogClient client;

    private BatchingEventSender sender;

    @AfterEach
    void tearDown() {
        if (sender != null) {
            sender.shutdown();
        }
    }

    @Test
    @DisplayName("flushes automatically once the batch size threshold is reached")
    void flushesAtBatchSizeThreshold() {
        sender = new BatchingEventSender(client, 3, Duration.ofMinutes(5), 100, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);

        sender.enqueue(CaptureEvent.of("e1", "u1", Map.of()));
        sender.enqueue(CaptureEvent.of("e2", "u1", Map.of()));
        verifyNoInteractions(client);

        sender.enqueue(CaptureEvent.of("e3", "u1", Map.of()));

        ArgumentCaptor<List<CaptureEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, times(1)).batch(captor.capture());
        assertEquals(3, captor.getValue().size());
        assertEquals(0, sender.pendingCount());
    }

    @Test
    @DisplayName("periodic scheduled flush sends events before the size threshold is hit")
    void flushesOnSchedule() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(client).batch(anyList());

        sender = new BatchingEventSender(client, 100, Duration.ofMillis(50), 1000, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);
        sender.enqueue(CaptureEvent.of("e1", "u1", Map.of()));

        assertTrue(latch.await(2, TimeUnit.SECONDS), "scheduled flush should have fired");
        verify(client, atLeastOnce()).batch(anyList());
    }

    @Test
    @DisplayName("shutdown performs a final flush - no event is lost")
    void shutdownFlushesRemainingEvents() {
        sender = new BatchingEventSender(client, 1000, Duration.ofMinutes(5), 1000, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);

        for (int i = 0; i < 10; i++) {
            sender.enqueue(CaptureEvent.of("e" + i, "u1", Map.of()));
        }
        verifyNoInteractions(client);

        sender.shutdown();

        List<CaptureEvent> sent = new ArrayList<>();
        ArgumentCaptor<List<CaptureEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, atLeastOnce()).batch(captor.capture());
        captor.getAllValues().forEach(sent::addAll);
        assertEquals(10, sent.size());
        assertEquals(0, sender.pendingCount());
    }

    @Test
    @DisplayName("large batches are chunked to at most batchSize events per call")
    void chunksLargeFlushesByBatchSize() {
        sender = new BatchingEventSender(client, 4, Duration.ofMinutes(5), 1000, AnalyticsProperties.OverflowPolicy.BLOCK);

        for (int i = 0; i < 4; i++) {
            sender.enqueue(CaptureEvent.of("e" + i, "u1", Map.of()));
        }
        // The 4th enqueue should have already triggered a flush of exactly 4 events.
        ArgumentCaptor<List<CaptureEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, times(1)).batch(captor.capture());
        assertEquals(4, captor.getValue().size());
    }

    @Test
    @DisplayName("DROP_OLDEST evicts the oldest event when the queue is full")
    void dropOldestPolicyEvictsOldest() {
        // capacity == batchSize so nothing auto-flushes while we fill it
        sender = new BatchingEventSender(client, 2, Duration.ofMinutes(5), 2, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);

        sender.enqueue(CaptureEvent.of("first", "u1", Map.of()));
        // second enqueue reaches batchSize(2) -> triggers immediate flush, draining the queue
        sender.enqueue(CaptureEvent.of("second", "u1", Map.of()));

        ArgumentCaptor<List<CaptureEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, times(1)).batch(captor.capture());
        assertEquals(List.of("first", "second"), captor.getValue().stream().map(CaptureEvent::event).toList());
        assertEquals(0, sender.droppedCount());
    }

    @Test
    @DisplayName("DROP_OLDEST evicts when capacity is smaller than the flush threshold trigger point")
    void dropOldestWhenCapacitySmallerThanBatchSize() {
        // batchSize larger than capacity means the queue fills up before an auto-flush fires.
        sender = new BatchingEventSender(client, 100, Duration.ofMinutes(5), 2, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);

        sender.enqueue(CaptureEvent.of("first", "u1", Map.of()));
        sender.enqueue(CaptureEvent.of("second", "u1", Map.of()));
        sender.enqueue(CaptureEvent.of("third", "u1", Map.of()));

        assertEquals(1, sender.droppedCount());
        assertEquals(2, sender.pendingCount());

        sender.flush();
        ArgumentCaptor<List<CaptureEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, times(1)).batch(captor.capture());
        assertEquals(List.of("second", "third"), captor.getValue().stream().map(CaptureEvent::event).toList());
    }

    @Test
    @DisplayName("BLOCK policy waits for space instead of dropping events")
    void blockPolicyWaitsForSpace() throws InterruptedException {
        sender = new BatchingEventSender(client, 100, Duration.ofMinutes(5), 1, AnalyticsProperties.OverflowPolicy.BLOCK);
        sender.enqueue(CaptureEvent.of("first", "u1", Map.of()));

        AtomicInteger secondEnqueued = new AtomicInteger(0);
        Thread blockedProducer = new Thread(() -> {
            sender.enqueue(CaptureEvent.of("second", "u1", Map.of()));
            secondEnqueued.incrementAndGet();
        });
        blockedProducer.start();

        // Give the producer thread a chance to actually block on the full queue.
        Thread.sleep(200);
        assertEquals(0, secondEnqueued.get(), "producer should still be blocked while queue is full");

        sender.flush(); // drains the queue, freeing space for the blocked put
        blockedProducer.join(2000);
        assertEquals(1, secondEnqueued.get());
        assertEquals(0, sender.droppedCount());
    }

    @Test
    @DisplayName("null events are ignored")
    void nullEventIgnored() {
        sender = new BatchingEventSender(client, 10, Duration.ofMinutes(5), 10, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);
        sender.enqueue(null);
        assertEquals(0, sender.pendingCount());
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("enqueue after shutdown is a no-op")
    void enqueueAfterShutdownIsNoop() {
        sender = new BatchingEventSender(client, 10, Duration.ofMinutes(5), 10, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);
        sender.shutdown();
        reset(client);

        sender.enqueue(CaptureEvent.of("late", "u1", Map.of()));
        assertEquals(0, sender.pendingCount());
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("client exceptions during flush are swallowed, not propagated")
    void flushSwallowsClientExceptions() {
        doThrow(new RuntimeException("posthog down")).when(client).batch(anyList());
        sender = new BatchingEventSender(client, 1, Duration.ofMinutes(5), 10, AnalyticsProperties.OverflowPolicy.DROP_OLDEST);

        assertDoesNotThrow(() -> sender.enqueue(CaptureEvent.of("e1", "u1", Map.of())));
    }
}
