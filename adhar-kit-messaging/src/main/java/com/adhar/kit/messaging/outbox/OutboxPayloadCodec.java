package com.adhar.kit.messaging.outbox;

import com.adhar.kit.messaging.exception.MessagingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Serializes outbox payloads to JSON for persistence and rebuilds the original
 * payload object before relay.
 * <p>
 * If the recorded payload type cannot be resolved (e.g. the class was renamed or is
 * not on the classpath of the relaying process), the raw JSON string is published
 * instead so the message is not lost.
 */
public class OutboxPayloadCodec {

    private static final Logger log = LoggerFactory.getLogger(OutboxPayloadCodec.class);

    private final ObjectMapper objectMapper;

    public OutboxPayloadCodec() {
        this(new ObjectMapper());
    }

    public OutboxPayloadCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Serializes a payload to JSON.
     *
     * @param payload the payload to serialize (must not be {@code null})
     * @return the JSON representation
     * @throws MessagingException if the payload cannot be serialized
     */
    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new MessagingException("Failed to serialize outbox payload of type "
                    + payload.getClass().getName(), e);
        }
    }

    /**
     * Deserializes a JSON payload back to the recorded type, falling back to the raw
     * JSON string when the type cannot be resolved.
     *
     * @param json        the JSON payload
     * @param payloadType fully-qualified class name recorded at enqueue time (may be {@code null})
     * @return the rebuilt payload object, or the raw JSON string as a fallback
     * @throws MessagingException if the type resolves but the JSON cannot be bound to it
     */
    public Object deserialize(String json, String payloadType) {
        if (payloadType == null || payloadType.isBlank() || String.class.getName().equals(payloadType)) {
            return payloadType != null && String.class.getName().equals(payloadType)
                    ? readAs(json, String.class)
                    : json;
        }
        Class<?> type;
        try {
            type = Class.forName(payloadType);
        } catch (ClassNotFoundException e) {
            log.warn("Outbox payload type {} is not on the classpath - relaying raw JSON instead", payloadType);
            return json;
        }
        return readAs(json, type);
    }

    private Object readAs(String json, Class<?> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new MessagingException("Failed to deserialize outbox payload as " + type.getName(), e);
        }
    }
}
