package com.adhar.kit.analytics.batching;

import com.adhar.kit.analytics.client.CaptureEvent;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BatchingEventSender retry/offline buffer Tests")
class BatchingEventSenderRetryTest {

    private BatchingEventSender sender;

    @AfterEach
    void tearDown() {
        if (sender != null) {
            sender.shutdown();
        }
    }

    /** Records every batch it is asked to send and can be told to fail. */
    private static final class RecordingClient implements PostHogClient {
        private final List<List<CaptureEvent>> sent = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean failing;
        private final AtomicInteger calls = new AtomicInteger();

        RecordingClient(boolean failing) {
            this.failing = failing;
        }

        @Override
        public void capture(CaptureEvent event) {
        }

        @Override
        public void batch(List<CaptureEvent> events) {
            calls.incrementAndGet();
            if (failing) {
                throw new RuntimeException("posthog down");
            }
            sent.add(new ArrayList<>(events));
        }

        @Override
        public com.adhar.kit.analytics.client.DecideResult decide(String distinctId) {
            return com.adhar.kit.analytics.client.DecideResult.empty();
        }
    }

    /** In-memory spill store; can be pre-seeded with batches to replay on startup. */
    private static final class FakeSpillStore implements SpillStore {
        private final List<List<CaptureEvent>> written = Collections.synchronizedList(new ArrayList<>());
        private final List<List<CaptureEvent>> toLoad = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void write(List<CaptureEvent> batch) {
            written.add(new ArrayList<>(batch));
        }

        @Override
        public List<List<CaptureEvent>> loadAndClear() {
            List<List<CaptureEvent>> copy = new ArrayList<>(toLoad);
            toLoad.clear();
            return copy;
        }
    }

    private static void awaitUntil(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("interrupted");
            }
        }
        assertTrue(condition.getAsBoolean(), "condition not met within " + timeoutMillis + "ms");
    }

    private RetrySettings fastRetry(int maxAttempts, int maxBatches) {
        return new RetrySettings(true, maxAttempts, 20, 2.0, 100, maxBatches);
    }

    @Test
    @DisplayName("a failed batch is retried and eventually delivered")
    void failedBatchRetriedThenSucceeds() {
        RecordingClient client = new RecordingClient(true);
        sender = new BatchingEventSender(client, 1, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, fastRetry(5, 100), null);

        sender.enqueue(CaptureEvent.of("e1", "u1", Map.of()));
        // First send fails; let a couple of retries happen, then let it succeed.
        awaitUntil(() -> client.calls.get() >= 2, 2000);
        client.failing = false;

        awaitUntil(() -> sender.retriedCount() >= 1, 2000);
        assertEquals(1, client.sent.size());
    }

    @Test
    @DisplayName("retries are exhausted then the batch is spilled to the offline store")
    void exhaustsRetriesThenSpills() {
        RecordingClient client = new RecordingClient(true);
        FakeSpillStore spill = new FakeSpillStore();
        sender = new BatchingEventSender(client, 1, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, fastRetry(2, 100), spill);

        sender.enqueue(CaptureEvent.of("e1", "u1", Map.of()));

        awaitUntil(() -> sender.spilledCount() >= 1, 3000);
        assertEquals(1, spill.written.size());
        assertEquals("e1", spill.written.get(0).get(0).event());
        assertEquals(0, sender.retryDroppedCount());
    }

    @Test
    @DisplayName("with no spill store, an exhausted batch is dropped and counted")
    void exhaustsRetriesThenDrops() {
        RecordingClient client = new RecordingClient(true);
        sender = new BatchingEventSender(client, 1, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, fastRetry(2, 100), null);

        sender.enqueue(CaptureEvent.of("e1", "u1", Map.of()));

        awaitUntil(() -> sender.retryDroppedCount() >= 1, 3000);
        assertEquals(0, sender.spilledCount());
    }

    @Test
    @DisplayName("spilled batches are reloaded and re-sent on startup")
    void reloadsSpillOnStartup() {
        RecordingClient client = new RecordingClient(false);
        FakeSpillStore spill = new FakeSpillStore();
        spill.toLoad.add(List.of(CaptureEvent.of("recovered", "u1", Map.of())));

        sender = new BatchingEventSender(client, 100, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, fastRetry(3, 100), spill);

        awaitUntil(() -> sender.retriedCount() >= 1, 2000);
        assertEquals(1, client.sent.size());
        assertEquals("recovered", client.sent.get(0).get(0).event());
    }

    @Test
    @DisplayName("when the retry queue is full, extra failed batches are spilled")
    void retryQueueBoundSpills() {
        RecordingClient client = new RecordingClient(true);
        FakeSpillStore spill = new FakeSpillStore();
        // Large backoff so nothing drains during the test; queue bound of 1.
        RetrySettings retry = new RetrySettings(true, 5, 10_000, 2.0, 10_000, 1);
        sender = new BatchingEventSender(client, 1, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, retry, spill);

        sender.enqueue(CaptureEvent.of("e1", "u1", Map.of())); // -> retry queue (size 1)
        sender.enqueue(CaptureEvent.of("e2", "u1", Map.of())); // -> queue full -> spill
        sender.enqueue(CaptureEvent.of("e3", "u1", Map.of())); // -> queue full -> spill

        assertEquals(1, sender.retryPendingCount());
        assertEquals(2, sender.spilledCount());
        assertEquals(2, spill.written.size());
    }

    @Test
    @DisplayName("shutdown spills any batches still awaiting retry")
    void shutdownSpillsPending() {
        RecordingClient client = new RecordingClient(true);
        FakeSpillStore spill = new FakeSpillStore();
        // Large backoff so the pending retry does not fire before shutdown.
        RetrySettings retry = new RetrySettings(true, 5, 10_000, 2.0, 10_000, 100);
        sender = new BatchingEventSender(client, 1, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, retry, spill);

        sender.enqueue(CaptureEvent.of("pending", "u1", Map.of()));
        awaitUntil(() -> sender.retryPendingCount() >= 1, 2000);

        sender.shutdown();
        sender = null; // already shut down

        assertEquals(1, spill.written.size());
        assertEquals("pending", spill.written.get(0).get(0).event());
    }

    @Test
    @DisplayName("legacy constructor keeps retry disabled - failures are dropped without retry")
    void legacyConstructorNoRetry() {
        RecordingClient client = new RecordingClient(true);
        sender = new BatchingEventSender(client, 1, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST);

        assertDoesNotThrow(() -> sender.enqueue(CaptureEvent.of("e1", "u1", Map.of())));
        assertEquals(0, sender.retryPendingCount());
        assertEquals(0, sender.retriedCount());
        assertEquals(0, sender.spilledCount());
    }
}
