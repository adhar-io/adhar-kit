package com.adhar.kit.eventsourcing.serialization;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maps {@link com.adhar.kit.eventsourcing.core.DomainEvent#eventType()} names to the Java
 * class that represents their typed payload.
 *
 * <p>{@code DomainEvent} intentionally keeps its payload as a plain {@code String} so the
 * core event-sourcing types stay serialization-agnostic. This registry lets callers opt in
 * to typed (de)serialization via {@link JacksonEventSerializer} without changing that contract.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class EventTypeRegistry {

    private final Map<String, Class<?>> typesByEventType = new ConcurrentHashMap<>();

    /**
     * Registers the payload class for a given event type name.
     *
     * @param eventType    the event type discriminator
     * @param payloadClass the Java class the payload should be deserialized into
     * @return this registry, for fluent registration
     */
    public EventTypeRegistry register(String eventType, Class<?> payloadClass) {
        typesByEventType.put(eventType, payloadClass);
        return this;
    }

    /**
     * Removes the registration for the given event type, if present.
     *
     * @param eventType the event type discriminator
     */
    public void unregister(String eventType) {
        typesByEventType.remove(eventType);
    }

    /**
     * Resolves the payload class registered for the given event type.
     *
     * @param eventType the event type discriminator
     * @return the registered class, or empty if none is registered
     */
    public Optional<Class<?>> resolve(String eventType) {
        return Optional.ofNullable(typesByEventType.get(eventType));
    }

    /**
     * Indicates whether a payload class has been registered for the given event type.
     *
     * @param eventType the event type discriminator
     * @return {@code true} if a class is registered
     */
    public boolean isRegistered(String eventType) {
        return typesByEventType.containsKey(eventType);
    }
}
