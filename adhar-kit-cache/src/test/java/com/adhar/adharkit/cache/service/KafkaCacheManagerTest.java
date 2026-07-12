package com.adhar.adharkit.cache.service;

import com.adhar.adharkit.cache.model.CacheMessage;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KafkaCacheManager} with a mocked {@link KafkaTemplate}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KafkaCacheManager Tests")
class KafkaCacheManagerTest {

    @Mock
    private KafkaTemplate<String, CacheMessage> kafkaTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdharCacheProperties properties;
    private KafkaCacheManager manager;

    @BeforeEach
    void setUp() {
        properties = new AdharCacheProperties();
        manager = new KafkaCacheManager(kafkaTemplate, properties, objectMapper);
    }

    @Test
    @DisplayName("has a non-null unique instance id")
    void testInstanceId() {
        assertNotNull(manager.getInstanceId());
        KafkaCacheManager other = new KafkaCacheManager(kafkaTemplate, properties, objectMapper);
        assertNotEquals(manager.getInstanceId(), other.getInstanceId());
    }

    @Test
    @DisplayName("getCache returns the same cache instance for the same name")
    void testGetCacheCaches() {
        Cache c1 = manager.getCache("users");
        Cache c2 = manager.getCache("users");
        assertNotNull(c1);
        assertSame(c1, c2);
        assertEquals("users", c1.getName());
        assertNotNull(c1.getNativeCache());
    }

    @Test
    @DisplayName("getCacheNames reflects accessed caches")
    void testGetCacheNames() {
        manager.getCache("users");
        assertTrue(manager.getCacheNames().contains("users"));
    }

    @Test
    @DisplayName("put stores value locally and publishes a PUT message")
    void testPutPublishes() {
        Cache cache = manager.getCache("users");
        cache.put("k1", "v1");

        assertEquals("v1", cache.get("k1", String.class));

        ArgumentCaptor<CacheMessage> captor = ArgumentCaptor.forClass(CacheMessage.class);
        verify(kafkaTemplate).send(eq("adhar-cache-put"), eq("k1"), captor.capture());
        CacheMessage msg = captor.getValue();
        assertEquals(CacheMessage.OperationType.PUT, msg.getOperationType());
        assertEquals("users", msg.getCacheName());
        assertEquals("k1", msg.getKey());
        assertEquals("java.lang.String", msg.getValueType());
        assertEquals(manager.getInstanceId(), msg.getSourceInstanceId());
    }

    @Test
    @DisplayName("evict removes value locally and publishes an EVICT message")
    void testEvictPublishes() {
        Cache cache = manager.getCache("users");
        cache.put("k1", "v1");
        cache.evict("k1");

        assertNull(cache.get("k1"));
        verify(kafkaTemplate).send(eq("adhar-cache-evict"), eq("k1"), any(CacheMessage.class));
    }

    @Test
    @DisplayName("clear empties the cache and publishes a CLEAR message")
    void testClearPublishes() {
        Cache cache = manager.getCache("users");
        cache.put("k1", "v1");
        cache.clear();

        assertNull(cache.get("k1"));
        verify(kafkaTemplate).send(eq("adhar-cache-clear"), eq("users"), any(CacheMessage.class));
    }

    @Test
    @DisplayName("put with TTL publishes a PUT message carrying timeToLive")
    void testPutWithTtl() {
        KafkaCacheManager.class.getName(); // sanity
        Cache cache = manager.getCache("users");
        // KafkaCache exposes an extended put(key, value, ttl, unit)
        invokePutWithTtl(cache, "k1", "v1", 5, TimeUnit.SECONDS);

        ArgumentCaptor<CacheMessage> captor = ArgumentCaptor.forClass(CacheMessage.class);
        verify(kafkaTemplate).send(eq("adhar-cache-put"), eq("k1"), captor.capture());
        assertEquals(Long.valueOf(5000L), captor.getValue().getTimeToLive());
    }

    @Test
    @DisplayName("no message is published when kafka sync is disabled")
    void testKafkaDisabled() {
        properties.getKafka().setEnabled(false);
        Cache cache = manager.getCache("users");
        cache.put("k1", "v1");
        cache.evict("k1");
        cache.clear();

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(CacheMessage.class));
    }

    @Test
    @DisplayName("get with value loader populates and returns computed value")
    void testGetWithLoader() {
        Cache cache = manager.getCache("users");
        String value = cache.get("k1", () -> "computed");
        assertEquals("computed", value);
    }

    /** Reflectively invokes the TTL-aware put overload on the private KafkaCache class. */
    private void invokePutWithTtl(Cache cache, Object key, Object value, long ttl, TimeUnit unit) {
        try {
            var method = cache.getClass().getDeclaredMethod("put", Object.class, Object.class, long.class, TimeUnit.class);
            method.setAccessible(true);
            method.invoke(cache, key, value, ttl, unit);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
