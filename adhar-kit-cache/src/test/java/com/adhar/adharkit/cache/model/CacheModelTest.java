package com.adhar.adharkit.cache.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for cache model types {@link CacheMessage} and {@link CacheOperation}.
 */
@DisplayName("Cache Model Tests")
class CacheModelTest {

    @Test
    @DisplayName("CacheOperation enum exposes PUT, EVICT, CLEAR")
    void testCacheOperation() {
        assertEquals(3, CacheOperation.values().length);
        assertEquals(CacheOperation.PUT, CacheOperation.valueOf("PUT"));
        assertEquals(CacheOperation.EVICT, CacheOperation.valueOf("EVICT"));
        assertEquals(CacheOperation.CLEAR, CacheOperation.valueOf("CLEAR"));
    }

    @Test
    @DisplayName("CacheMessage.OperationType enum exposes PUT, EVICT, CLEAR")
    void testOperationType() {
        assertEquals(3, CacheMessage.OperationType.values().length);
        assertEquals(CacheMessage.OperationType.PUT, CacheMessage.OperationType.valueOf("PUT"));
    }

    @Test
    @DisplayName("CacheMessage builder and accessors work")
    void testBuilderAndAccessors() {
        Instant now = Instant.now();
        CacheMessage msg = CacheMessage.builder()
            .operationType(CacheMessage.OperationType.PUT)
            .cacheName("users")
            .key("k1")
            .value("\"v\"")
            .valueType("java.lang.String")
            .timeToLive(1000L)
            .timestamp(now)
            .sourceInstanceId("instance-1")
            .build();

        assertEquals(CacheMessage.OperationType.PUT, msg.getOperationType());
        assertEquals("users", msg.getCacheName());
        assertEquals("k1", msg.getKey());
        assertEquals("\"v\"", msg.getValue());
        assertEquals("java.lang.String", msg.getValueType());
        assertEquals(1000L, msg.getTimeToLive());
        assertEquals(now, msg.getTimestamp());
        assertEquals("instance-1", msg.getSourceInstanceId());
    }

    @Test
    @DisplayName("CacheMessage no-args constructor with setters, equals/hashCode/toString")
    void testNoArgsAndEquality() {
        CacheMessage a = new CacheMessage();
        a.setCacheName("c");
        a.setKey("k");
        a.setOperationType(CacheMessage.OperationType.EVICT);

        CacheMessage b = new CacheMessage();
        b.setCacheName("c");
        b.setKey("k");
        b.setOperationType(CacheMessage.OperationType.EVICT);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotNull(a.toString());

        b.setKey("other");
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("CacheMessage all-args constructor populates all fields")
    void testAllArgsConstructor() {
        Instant now = Instant.now();
        CacheMessage msg = new CacheMessage(
            CacheMessage.OperationType.CLEAR, "users", "k", "v", "java.lang.String", 500L, now, "src");
        assertEquals(CacheMessage.OperationType.CLEAR, msg.getOperationType());
        assertEquals(500L, msg.getTimeToLive());
        assertEquals("src", msg.getSourceInstanceId());
    }
}
