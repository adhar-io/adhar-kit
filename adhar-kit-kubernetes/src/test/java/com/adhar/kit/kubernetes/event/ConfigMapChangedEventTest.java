package com.adhar.kit.kubernetes.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMapChangedEventTest {

    @Test
    void exposesFieldsAndDefensivelyCopiesData() {
        ConfigMapChangedEvent event = new ConfigMapChangedEvent(this, "app-config", "default",
                Map.of("key", "value"), ChangeType.MODIFIED);

        assertEquals("app-config", event.getName());
        assertEquals("default", event.getNamespace());
        assertEquals("value", event.getData().get("key"));
        assertEquals(ChangeType.MODIFIED, event.getChangeType());
        assertThrows(UnsupportedOperationException.class, () -> event.getData().put("x", "y"));
    }

    @Test
    void handlesNullDataGracefully() {
        ConfigMapChangedEvent event = new ConfigMapChangedEvent(this, "app-config", "default",
                null, ChangeType.DELETED);

        assertTrue(event.getData().isEmpty());
    }
}
