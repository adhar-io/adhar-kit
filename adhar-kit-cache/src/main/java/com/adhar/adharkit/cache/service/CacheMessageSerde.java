package com.adhar.adharkit.cache.service;

import com.adhar.adharkit.cache.model.CacheMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.errors.SerializationException;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;

/**
 * Kafka (de)serializer for {@link CacheMessage}, using the module's own Jackson
 * mapper with {@code java.time} support.
 * <p>
 * Replaces spring-kafka's {@code JsonSerializer}/{@code JsonDeserializer}, whose
 * Jackson 2 mapper cannot write {@link CacheMessage#getTimestamp()} in a Spring Boot
 * 4 application (Jackson 3 there; the Jackson 2 JSR-310 module is not on the
 * classpath), so every synchronization message failed to send.
 */
public final class CacheMessageSerde implements Serializer<CacheMessage>, Deserializer<CacheMessage> {

    /** Mapper shared by the cache's Kafka synchronization: messages and the values they carry. */
    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Override
    public byte[] serialize(String topic, CacheMessage message) {
        if (message == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsBytes(message);
        } catch (IOException e) {
            throw new SerializationException("Cannot serialize cache message for topic " + topic, e);
        }
    }

    @Override
    public void configure(java.util.Map<String, ?> configs, boolean isKey) {
        // nothing to configure
    }

    @Override
    public void close() {
        // stateless
    }

    @Override
    public CacheMessage deserialize(String topic, byte[] data) {
        if (data == null) {
            return null;
        }
        try {
            return MAPPER.readValue(data, CacheMessage.class);
        } catch (IOException e) {
            throw new SerializationException("Cannot deserialize cache message from topic " + topic, e);
        }
    }
}
