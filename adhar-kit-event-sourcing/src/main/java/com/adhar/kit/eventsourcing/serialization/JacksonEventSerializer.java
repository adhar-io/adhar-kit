package com.adhar.kit.eventsourcing.serialization;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Jackson-backed helper for converting between typed event payload objects and the
 * {@code String} payload carried by {@link DomainEvent}.
 *
 * <p>{@code DomainEvent} keeps its payload as a plain JSON string for storage
 * simplicity and backward compatibility; this class is an opt-in convenience for callers
 * that want typed payloads without changing that contract.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class JacksonEventSerializer {

    private final ObjectMapper objectMapper;
    private final EventTypeRegistry typeRegistry;

    public JacksonEventSerializer(ObjectMapper objectMapper, EventTypeRegistry typeRegistry) {
        this.objectMapper = objectMapper;
        this.typeRegistry = typeRegistry;
    }

    /**
     * Serializes an arbitrary payload object to a JSON string suitable for
     * {@link DomainEvent#payload()}.
     *
     * @param payload the payload object to serialize
     * @return the JSON representation
     * @throws IllegalArgumentException if serialization fails
     */
    public String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize event payload of type "
                    + payload.getClass().getName(), ex);
        }
    }

    /**
     * Deserializes an event's JSON payload into the given target type.
     *
     * @param event      the domain event whose payload should be deserialized
     * @param targetType the class to deserialize into
     * @param <T>        the target type
     * @return the typed payload
     * @throws IllegalArgumentException if deserialization fails
     */
    public <T> T deserialize(DomainEvent event, Class<T> targetType) {
        try {
            return objectMapper.readValue(event.payload(), targetType);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Failed to deserialize payload for event type '" + event.eventType() + "'", ex);
        }
    }

    /**
     * Produces a typed payload for the given event by looking up its registered class in
     * the {@link EventTypeRegistry}.
     *
     * @param event the domain event to deserialize
     * @return the typed payload, or empty if no class is registered for the event's type
     * @throws IllegalArgumentException if a class is registered but deserialization fails
     */
    public Optional<Object> deserializeTyped(DomainEvent event) {
        return typeRegistry.resolve(event.eventType())
                .map(type -> deserialize(event, type));
    }
}
