package com.adhar.kit.eventsourcing.bus;

import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.serialization.EventTypeRegistry;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;

/**
 * Serializes and deserializes {@link DomainEvent} envelopes to and from the JSON wire format
 * used by the {@link KafkaEventBus}.
 *
 * <p>The full {@code DomainEvent} record is serialized as the message body. On the read path the
 * envelope is reconstructed and the {@link EventTypeRegistry} is consulted to validate that the
 * carried {@code payload} can still be parsed into the registered payload class for the event's
 * {@code eventType()}. Validation failures are logged but never abort delivery, mirroring the
 * lenient dispatch behaviour of the in-process bus.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DomainEventKafkaSerde {

    private final ObjectMapper objectMapper;
    private final EventTypeRegistry typeRegistry;

    public DomainEventKafkaSerde(ObjectMapper objectMapper, EventTypeRegistry typeRegistry) {
        // Copy so the shared mapper is not mutated, and register ISO-8601 Instant handling so the
        // envelope's occurredAt field serializes without depending on jackson-datatype-jsr310 being
        // on the classpath.
        this.objectMapper = objectMapper.copy().registerModule(instantModule());
        this.typeRegistry = typeRegistry;
    }

    private static SimpleModule instantModule() {
        SimpleModule module = new SimpleModule("EventSourcingInstantModule");
        module.addSerializer(Instant.class, new JsonSerializer<>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addDeserializer(Instant.class, new JsonDeserializer<>() {
            @Override
            public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
                return Instant.parse(parser.getValueAsString());
            }
        });
        return module;
    }

    /**
     * Serializes a domain event envelope to its JSON wire representation.
     *
     * @param event the domain event to serialize
     * @return the JSON representation
     * @throws IllegalArgumentException if serialization fails
     */
    public String serialize(DomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(
                    "Failed to serialize domain event '" + event.eventType() + "' for aggregate '"
                            + event.aggregateId() + "'", ex);
        }
    }

    /**
     * Reconstructs a domain event envelope from its JSON wire representation.
     *
     * <p>If a payload class is registered for the event type, the payload is validated by
     * attempting to parse it into that class; a failure is logged but the reconstructed event is
     * still returned so a single incompatible payload cannot stall the subscription.</p>
     *
     * @param message the JSON message body
     * @return the reconstructed domain event
     * @throws IllegalArgumentException if the envelope itself cannot be parsed
     */
    public DomainEvent deserialize(String message) {
        DomainEvent event;
        try {
            event = objectMapper.readValue(message, DomainEvent.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize domain event envelope", ex);
        }
        typeRegistry.resolve(event.eventType()).ifPresent(payloadType -> {
            try {
                objectMapper.readValue(event.payload(), payloadType);
            } catch (JsonProcessingException ex) {
                log.warn("Payload for event type '{}' (aggregate '{}') is not compatible with the "
                                + "registered class {}: {}", event.eventType(), event.aggregateId(),
                        payloadType.getName(), ex.getOriginalMessage());
            }
        });
        return event;
    }
}
