package com.adhar.kit.analytics.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CaptureEvent Tests")
class CaptureEventTest {

    @Test
    @DisplayName("null properties/timestamp default to empty map / now")
    void nullsDefault() {
        CaptureEvent event = new CaptureEvent("Event", "user-1", null, null);

        assertTrue(event.properties().isEmpty());
        assertNotNull(event.timestamp());
    }

    @Test
    @DisplayName("of() stamps the current time and preserves given properties")
    void ofFactoryStampsNow() {
        Instant before = Instant.now();
        CaptureEvent event = CaptureEvent.of("Event", "user-1", Map.of("k", "v"));
        Instant after = Instant.now();

        assertEquals("v", event.properties().get("k"));
        assertFalse(event.timestamp().isBefore(before));
        assertFalse(event.timestamp().isAfter(after.plusSeconds(1)));
    }

    @Test
    @DisplayName("properties map is defensively copied and immutable")
    void propertiesAreImmutable() {
        Map<String, Object> mutable = new java.util.HashMap<>(Map.of("a", 1));
        CaptureEvent event = new CaptureEvent("Event", "user-1", mutable, Instant.now());

        mutable.put("b", 2);
        assertFalse(event.properties().containsKey("b"), "event properties should be a defensive copy");
        assertThrows(UnsupportedOperationException.class, () -> event.properties().put("c", 3));
    }
}
