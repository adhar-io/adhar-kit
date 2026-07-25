package com.adhar.kit.kubernetes.event;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretChangedEventTest {

    @Test
    void exposesFieldsAndDefensivelyCopiesData() {
        SecretChangedEvent event = new SecretChangedEvent(this, "db-secret", "default",
                Map.of("password", "s3cr3t"), ChangeType.ADDED);

        assertEquals("db-secret", event.getName());
        assertEquals("default", event.getNamespace());
        assertEquals("s3cr3t", event.getData().get("password"));
        assertEquals(ChangeType.ADDED, event.getChangeType());
        assertThrows(UnsupportedOperationException.class, () -> event.getData().put("x", "y"));
    }

    @Test
    void handlesNullDataGracefully() {
        SecretChangedEvent event = new SecretChangedEvent(this, "db-secret", "default",
                null, ChangeType.DELETED);

        assertTrue(event.getData().isEmpty());
    }
}
