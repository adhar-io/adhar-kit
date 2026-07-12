package com.adhar.kit.analytics.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AnalyticsEvent Model Tests")
class AnalyticsEventTest {

    @Test
    @DisplayName("builder populates fields")
    void builder() {
        LocalDateTime ts = LocalDateTime.now();
        AnalyticsEvent e = AnalyticsEvent.builder()
                .eventId("e1")
                .eventType("login")
                .category("user")
                .timestamp(ts)
                .userId("u1")
                .sessionId("s1")
                .source("api")
                .properties(Map.of("k", "v"))
                .metadata(Map.of("m", "n"))
                .ipAddress("127.0.0.1")
                .userAgent("agent")
                .critical(true)
                .build();

        assertEquals("e1", e.getEventId());
        assertEquals("login", e.getEventType());
        assertEquals("user", e.getCategory());
        assertEquals(ts, e.getTimestamp());
        assertEquals("u1", e.getUserId());
        assertEquals("s1", e.getSessionId());
        assertEquals("api", e.getSource());
        assertEquals("v", e.getProperties().get("k"));
        assertEquals("n", e.getMetadata().get("m"));
        assertEquals("127.0.0.1", e.getIpAddress());
        assertEquals("agent", e.getUserAgent());
        assertTrue(e.isCritical());
    }

    @Test
    @DisplayName("no-args constructor with setters")
    void noArgsAndSetters() {
        AnalyticsEvent e = new AnalyticsEvent();
        e.setEventId("x");
        e.setEventType("t");
        e.setCritical(false);
        assertEquals("x", e.getEventId());
        assertEquals("t", e.getEventType());
        assertFalse(e.isCritical());
    }

    @Test
    @DisplayName("equals, hashCode and toString")
    void equalsHashCodeToString() {
        AnalyticsEvent a = AnalyticsEvent.builder().eventId("id").eventType("t").build();
        AnalyticsEvent b = AnalyticsEvent.builder().eventId("id").eventType("t").build();
        AnalyticsEvent c = AnalyticsEvent.builder().eventId("other").build();

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertTrue(a.toString().contains("AnalyticsEvent"));
    }
}
