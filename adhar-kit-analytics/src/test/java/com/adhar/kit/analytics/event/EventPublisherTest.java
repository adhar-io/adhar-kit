package com.adhar.kit.analytics.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventPublisher Tests")
class EventPublisherTest {

    @Mock
    private KafkaTemplate<String, AnalyticsEvent> kafkaTemplate;

    private EventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new EventPublisher(kafkaTemplate);
    }

    @Test
    @DisplayName("publishEvent assigns id and timestamp when missing")
    void publishEventDefaults() {
        AnalyticsEvent event = AnalyticsEvent.builder().eventType("test").build();

        publisher.publishEvent(event);

        assertNotNull(event.getEventId());
        assertNotNull(event.getTimestamp());
        verify(kafkaTemplate).send(eq("analytics-events"), eq(event.getEventId()), eq(event));
    }

    @Test
    @DisplayName("publishEvent preserves provided id and timestamp")
    void publishEventPreservesValues() {
        var ts = java.time.LocalDateTime.of(2024, 1, 1, 0, 0);
        AnalyticsEvent event = AnalyticsEvent.builder()
                .eventId("fixed-id").eventType("test").timestamp(ts).build();

        publisher.publishEvent(event);

        assertEquals("fixed-id", event.getEventId());
        assertEquals(ts, event.getTimestamp());
        verify(kafkaTemplate).send("analytics-events", "fixed-id", event);
    }

    @Test
    @DisplayName("publishEvent swallows kafka failures")
    void publishEventHandlesException() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenThrow(new RuntimeException("kafka down"));
        AnalyticsEvent event = AnalyticsEvent.builder().eventType("test").build();

        assertDoesNotThrow(() -> publisher.publishEvent(event));
    }

    @Test
    @DisplayName("trackUserAction builds user_action event")
    void trackUserAction() {
        publisher.trackUserAction("u1", "click", Map.of("x", 1));

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(kafkaTemplate).send(eq("analytics-events"), anyString(), captor.capture());
        AnalyticsEvent e = captor.getValue();
        assertEquals("user.click", e.getEventType());
        assertEquals("u1", e.getUserId());
        assertEquals("user_action", e.getCategory());
        assertEquals(1, e.getProperties().get("x"));
    }

    @Test
    @DisplayName("trackBusinessEvent builds business event")
    void trackBusinessEvent() {
        publisher.trackBusinessEvent("order.created", "u2", Map.of("total", 99));

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(kafkaTemplate).send(eq("analytics-events"), anyString(), captor.capture());
        AnalyticsEvent e = captor.getValue();
        assertEquals("order.created", e.getEventType());
        assertEquals("business", e.getCategory());
    }

    @Test
    @DisplayName("trackPageView builds page.view event with null extra properties")
    void trackPageViewNullProps() {
        publisher.trackPageView("u4", "s4", "/about", null);

        ArgumentCaptor<AnalyticsEvent> captor = ArgumentCaptor.forClass(AnalyticsEvent.class);
        verify(kafkaTemplate).send(eq("analytics-events"), anyString(), captor.capture());
        AnalyticsEvent e = captor.getValue();
        assertEquals("page.view", e.getEventType());
        assertEquals("navigation", e.getCategory());
        assertEquals("s4", e.getSessionId());
        assertEquals("/about", e.getProperties().get("page"));
    }

    @Test
    @DisplayName("trackPageView with extra properties currently fails (immutable Map.of bug)")
    void trackPageViewWithExtraPropsThrows() {
        // EventPublisher initializes properties with an immutable Map.of(...) and then
        // calls putAll on it when extra properties are supplied -> UnsupportedOperationException.
        assertThrows(UnsupportedOperationException.class,
                () -> publisher.trackPageView("u3", "s3", "/home", Map.of("ref", "google")));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }
}
