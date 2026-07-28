package com.adhar.kit.test.junit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ContainerConnectionInfo}.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("ContainerConnectionInfo Tests")
class ContainerConnectionInfoTest {

    @BeforeEach
    @AfterEach
    void reset() {
        ContainerConnectionInfo.getInstance().clear();
    }

    @Test
    @DisplayName("getInstance should be a singleton")
    void testSingleton() {
        assertSame(ContainerConnectionInfo.getInstance(), ContainerConnectionInfo.getInstance());
    }

    @Test
    @DisplayName("put should store non-null values and ignore nulls")
    void testPut() {
        ContainerConnectionInfo info = ContainerConnectionInfo.getInstance();
        info.put("a", "1");
        info.put("b", null);

        assertEquals("1", info.get("a"));
        assertTrue(info.has("a"));
        assertNull(info.get("b"));
        assertFalse(info.has("b"));
    }

    @Test
    @DisplayName("putAll should copy every non-null entry")
    void testPutAll() {
        Map<String, String> values = new HashMap<>();
        values.put("x", "1");
        values.put("y", null);
        ContainerConnectionInfo.getInstance().putAll(values);

        assertEquals("1", ContainerConnectionInfo.getInstance().get("x"));
        assertFalse(ContainerConnectionInfo.getInstance().has("y"));
    }

    @Test
    @DisplayName("asMap should return an immutable snapshot")
    void testAsMap() {
        ContainerConnectionInfo.getInstance().put("k", "v");
        Map<String, String> snapshot = ContainerConnectionInfo.getInstance().asMap();

        assertEquals(1, snapshot.size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put("z", "1"));
    }

    @Test
    @DisplayName("clear should remove everything")
    void testClear() {
        ContainerConnectionInfo.getInstance().put("k", "v");
        ContainerConnectionInfo.getInstance().clear();
        assertFalse(ContainerConnectionInfo.getInstance().has("k"));
    }
}
