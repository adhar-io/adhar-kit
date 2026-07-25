package com.adhar.kit.analytics;

import com.adhar.kit.analytics.client.CaptureEvent;
import com.adhar.kit.analytics.client.PostHogClient;
import com.adhar.kit.analytics.config.AnalyticsProperties;
import com.adhar.kit.analytics.consent.ConsentGateway;
import com.adhar.kit.analytics.consent.InMemoryConsentStore;
import com.adhar.kit.analytics.pii.PiiScrubber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies AnalyticsFacade's real async batching path (bug #1: previously
 * every event was posted synchronously one-by-one and flush()/shutdown()
 * were no-ops).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsFacade batching Tests")
class AnalyticsFacadeBatchingTest {

    @Mock
    private PostHogClient client;

    private AnalyticsFacade facade;

    @AfterEach
    void tearDown() {
        if (facade != null && facade.isAvailable()) {
            facade.shutdown();
        }
    }

    @Test
    @DisplayName("track() enqueues rather than sending synchronously, and flushes at the batch size threshold")
    void tracksAreBatchedAndFlushedAtSizeThreshold() {
        facade = new AnalyticsFacade(client, true, 3, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, Duration.ofSeconds(60), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()), new PiiScrubber(Set.of(), false));

        facade.track("u1", "e1");
        facade.track("u1", "e2");
        verify(client, never()).capture(any());
        verify(client, never()).batch(anyList());

        facade.track("u1", "e3");

        ArgumentCaptor<List<CaptureEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, times(1)).batch(captor.capture());
        assertEquals(3, captor.getValue().size());
    }

    @Test
    @DisplayName("flush() sends buffered events immediately even below the batch size threshold")
    void flushSendsBelowThreshold() {
        facade = new AnalyticsFacade(client, true, 100, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, Duration.ofSeconds(60), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()), new PiiScrubber(Set.of(), false));

        facade.track("u1", "e1");
        verifyNoInteractions(client);

        facade.flush();

        verify(client, times(1)).batch(anyList());
    }

    @Test
    @DisplayName("shutdown() flushes any remaining buffered events - no event is lost")
    void shutdownFlushesRemainingEvents() {
        facade = new AnalyticsFacade(client, true, 100, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, Duration.ofSeconds(60), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()), new PiiScrubber(Set.of(), false));

        for (int i = 0; i < 5; i++) {
            facade.track("u1", "e" + i);
        }
        verifyNoInteractions(client);

        facade.shutdown();

        List<CaptureEvent> sent = new ArrayList<>();
        ArgumentCaptor<List<CaptureEvent>> captor = ArgumentCaptor.forClass(List.class);
        verify(client, atLeastOnce()).batch(captor.capture());
        captor.getAllValues().forEach(sent::addAll);
        assertEquals(5, sent.size());
        assertFalse(facade.isAvailable(), "shutdown() should mark analytics unavailable");
    }

    @Test
    @DisplayName("after shutdown(), further track() calls are no-ops")
    void shutdownDisablesFurtherTracking() {
        facade = new AnalyticsFacade(client, true, 100, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, Duration.ofSeconds(60), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()), new PiiScrubber(Set.of(), false));

        facade.shutdown();
        reset(client);

        facade.track("u1", "late-event");
        verifyNoInteractions(client);
    }

    @Test
    @DisplayName("synchronous fallback (batchingEnabled=false) sends via capture() immediately, no batch queue")
    void synchronousFallbackSendsImmediately() {
        facade = new AnalyticsFacade(client, false, 100, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, Duration.ofSeconds(60), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()), new PiiScrubber(Set.of(), false));

        facade.track("u1", "e1");

        verify(client, times(1)).capture(any());
        verify(client, never()).batch(anyList());
    }

    @Test
    @DisplayName("health() reports availability, provider and batching diagnostics")
    void healthReportsBatchingDiagnostics() {
        facade = new AnalyticsFacade(client, true, 2, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, Duration.ofSeconds(60), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()), new PiiScrubber(Set.of(), false));

        facade.track("u1", "e1");

        Map<String, Object> health = facade.health();
        assertEquals(true, health.get("available"));
        assertEquals("posthog", health.get("provider"));
        assertEquals(1, health.get("pendingEvents"));
        assertEquals(0L, health.get("droppedEvents"));
    }

    @Test
    @DisplayName("trackAsCloudEvent() still batches the underlying event and returns the CloudEvent envelope")
    void trackAsCloudEventBatchesUnderlyingEvent() {
        facade = new AnalyticsFacade(client, false, 100, Duration.ofMinutes(5), 100,
                AnalyticsProperties.OverflowPolicy.DROP_OLDEST, Duration.ofSeconds(60), Clock.systemUTC(),
                new ConsentGateway(new InMemoryConsentStore()), new PiiScrubber(Set.of(), false));

        var cloudEvent = facade.trackAsCloudEvent("u1", "Purchase", Map.of("amount", 10));

        assertNotNull(cloudEvent);
        assertEquals("com.adhar.analytics.tracked", cloudEvent.type());
        verify(client, times(1)).capture(any());
    }
}
