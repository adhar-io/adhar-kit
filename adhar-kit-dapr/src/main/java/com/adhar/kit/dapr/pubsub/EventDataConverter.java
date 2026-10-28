package com.adhar.kit.dapr.pubsub;

import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Converts the {@code data} of a delivered CloudEvent to the type a subscriber asked for, the
 * same way whether the event came over HTTP, a streaming subscription or memory mode.
 *
 * <p>JSON payloads are converted with the kit mapper; a JSON string holding a JSON document (what
 * some publishers send) is parsed first; {@code data_base64} is decoded for {@code byte[]}
 * subscribers. A payload that cannot become the requested type throws
 * {@link DropEventException}: redelivering it would fail the same way forever.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class EventDataConverter {

    private final ObjectMapper mapper;

    public EventDataConverter(DaprJsonSerializer serializer) {
        this.mapper = (serializer == null ? new DaprJsonSerializer() : serializer).objectMapper();
    }

    /**
     * The event's payload as {@code type}.
     *
     * @param envelope the CloudEvent as delivered
     * @param type     the requested type
     * @return the converted payload (may be {@code null})
     * @throws DropEventException when the payload cannot be converted
     */
    public Object data(Map<String, Object> envelope, Type type) {
        Object data = envelope.containsKey("data") ? envelope.get("data") : null;
        if (data == null && envelope.get("data_base64") instanceof String base64) {
            data = Base64.getDecoder().decode(base64);
        }
        return convert(data, type);
    }

    /**
     * Converts a payload value to {@code type}.
     *
     * @param data the payload
     * @param type the requested type
     * @return the converted payload
     * @throws DropEventException when the payload cannot be converted
     */
    public Object convert(Object data, Type type) {
        if (data == null) {
            return null;
        }
        JavaType target = mapper.constructType(type);
        Class<?> raw = target.getRawClass();
        try {
            if (raw == Object.class || raw.isInstance(data) && !target.hasGenericTypes()) {
                return data;
            }
            if (raw == byte[].class) {
                if (data instanceof String text) {
                    return text.getBytes(StandardCharsets.UTF_8);
                }
                return mapper.writeValueAsBytes(data);
            }
            if (raw == String.class) {
                return data instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8)
                        : data instanceof String text ? text : mapper.writeValueAsString(data);
            }
            if (data instanceof byte[] bytes) {
                return mapper.readValue(bytes, target);
            }
            if (data instanceof String text && looksLikeJson(text) && !raw.isEnum()) {
                return mapper.readValue(text, target);
            }
            return mapper.convertValue(data, target);
        } catch (IOException | IllegalArgumentException e) {
            throw new DropEventException("Event data cannot be read as " + type.getTypeName() + ": "
                    + e.getMessage(), e);
        }
    }

    private static boolean looksLikeJson(String text) {
        String t = text.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }
}
