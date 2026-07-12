package com.adhar.adharkit.cache.service;

import com.adhar.adharkit.cache.model.CacheMessage;
import com.adhar.adharkit.cache.properties.AdharCacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link KafkaCacheListener} with mocked Spring {@link CacheManager}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("KafkaCacheListener Tests")
class KafkaCacheListenerTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AdharCacheProperties properties;
    private KafkaCacheListener listener;

    @BeforeEach
    void setUp() {
        properties = new AdharCacheProperties();
        listener = new KafkaCacheListener(cacheManager, properties, objectMapper);
        when(cacheManager.getCache("users")).thenReturn(cache);
        when(cacheManager.getCache("missing")).thenReturn(null);
    }

    private CacheMessage putMessage() {
        return CacheMessage.builder()
            .operationType(CacheMessage.OperationType.PUT)
            .cacheName("users")
            .key("k1")
            .value("\"hello\"")
            .valueType("java.lang.String")
            .timestamp(Instant.now())
            .build();
    }

    // ---------- PUT ----------

    @Test
    @DisplayName("PUT applies deserialized value to the cache")
    void testPut() {
        listener.listenForPutMessages(putMessage());
        verify(cache).put("k1", "hello");
    }

    @Test
    @DisplayName("PUT ignores null message")
    void testPutNull() {
        listener.listenForPutMessages(null);
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("PUT does nothing when cache not found")
    void testPutCacheMissing() {
        CacheMessage msg = putMessage();
        msg.setCacheName("missing");
        listener.listenForPutMessages(msg);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("PUT swallows exceptions thrown while applying (bad value type)")
    void testPutBadType() {
        CacheMessage msg = putMessage();
        msg.setValueType("com.does.not.Exist");
        // Class.forName throws -> caught, no put
        listener.listenForPutMessages(msg);
        verify(cache, never()).put(any(), any());
    }

    @Test
    @DisplayName("PUT with null value type stores null")
    void testPutNullValueType() {
        CacheMessage msg = putMessage();
        msg.setValueType(null);
        listener.listenForPutMessages(msg);
        verify(cache).put("k1", null);
    }

    // ---------- EVICT ----------

    @Test
    @DisplayName("EVICT removes the key from the cache")
    void testEvict() {
        CacheMessage msg = CacheMessage.builder()
            .operationType(CacheMessage.OperationType.EVICT)
            .cacheName("users").key("k1").build();
        listener.listenForEvictMessages(msg);
        verify(cache).evict("k1");
    }

    @Test
    @DisplayName("EVICT ignores null message")
    void testEvictNull() {
        listener.listenForEvictMessages(null);
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("EVICT does nothing when cache not found")
    void testEvictMissing() {
        CacheMessage msg = CacheMessage.builder()
            .operationType(CacheMessage.OperationType.EVICT)
            .cacheName("missing").key("k1").build();
        listener.listenForEvictMessages(msg);
        verify(cache, never()).evict(any());
    }

    @Test
    @DisplayName("EVICT swallows exceptions from cache")
    void testEvictException() {
        doThrow(new RuntimeException("boom")).when(cache).evict(any());
        CacheMessage msg = CacheMessage.builder()
            .operationType(CacheMessage.OperationType.EVICT)
            .cacheName("users").key("k1").build();
        listener.listenForEvictMessages(msg);
        verify(cache).evict("k1");
    }

    // ---------- CLEAR ----------

    @Test
    @DisplayName("CLEAR clears the cache")
    void testClear() {
        CacheMessage msg = CacheMessage.builder()
            .operationType(CacheMessage.OperationType.CLEAR)
            .cacheName("users").build();
        listener.listenForClearMessages(msg);
        verify(cache).clear();
    }

    @Test
    @DisplayName("CLEAR ignores null message")
    void testClearNull() {
        listener.listenForClearMessages(null);
        verifyNoInteractions(cache);
    }

    @Test
    @DisplayName("CLEAR does nothing when cache not found")
    void testClearMissing() {
        CacheMessage msg = CacheMessage.builder()
            .operationType(CacheMessage.OperationType.CLEAR)
            .cacheName("missing").build();
        listener.listenForClearMessages(msg);
        verify(cache, never()).clear();
    }

    @Test
    @DisplayName("CLEAR swallows exceptions from cache")
    void testClearException() {
        doThrow(new RuntimeException("boom")).when(cache).clear();
        CacheMessage msg = CacheMessage.builder()
            .operationType(CacheMessage.OperationType.CLEAR)
            .cacheName("users").build();
        listener.listenForClearMessages(msg);
        verify(cache).clear();
    }

    // ---------- isMessageFromThisInstance ----------

    @Test
    @DisplayName("Skips message originating from this KafkaCacheManager instance")
    void testSkipOwnInstance() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, CacheMessage> template = mock(KafkaTemplate.class);
        KafkaCacheManager kafkaManager = new KafkaCacheManager(template, properties, objectMapper);
        KafkaCacheListener kafkaListener = new KafkaCacheListener(kafkaManager, properties, objectMapper);

        CacheMessage msg = putMessage();
        msg.setSourceInstanceId(kafkaManager.getInstanceId());

        // Should be skipped: cache should never be fetched/updated
        kafkaListener.listenForPutMessages(msg);
        // No assertion on cache mock here (different manager); just ensure no exception
    }

    @Test
    @DisplayName("Processes message from a different KafkaCacheManager instance")
    void testProcessOtherInstance() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, CacheMessage> template = mock(KafkaTemplate.class);
        KafkaCacheManager kafkaManager = spy(new KafkaCacheManager(template, properties, objectMapper));
        Cache kCache = mock(Cache.class);
        doReturn(kCache).when(kafkaManager).getCache("users");
        KafkaCacheListener kafkaListener = new KafkaCacheListener(kafkaManager, properties, objectMapper);

        CacheMessage msg = putMessage();
        msg.setSourceInstanceId("some-other-instance-id");

        kafkaListener.listenForPutMessages(msg);
        verify(kCache).put("k1", "hello");
    }

    @Test
    @DisplayName("Null source instance id is treated as another instance and processed")
    void testNullSourceProcessed() {
        CacheMessage msg = putMessage();
        msg.setSourceInstanceId(null);
        listener.listenForPutMessages(msg);
        verify(cache).put("k1", "hello");
    }
}
