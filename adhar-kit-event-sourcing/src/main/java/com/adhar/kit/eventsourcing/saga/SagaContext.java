package com.adhar.kit.eventsourcing.saga;

import java.util.Map;

/**
 * Mutable context shared across the steps of a single saga execution.
 *
 * <p>Carries the saga's correlation id and a free-form data bag that step actions and
 * compensations read from and write to. The backing map is the {@link SagaInstance}'s own data, so
 * mutations made here are persisted whenever the instance is saved by the {@link SagaManager}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class SagaContext {

    private final String correlationId;
    private final Map<String, Object> data;

    public SagaContext(String correlationId, Map<String, Object> data) {
        this.correlationId = correlationId;
        this.data = data;
    }

    /**
     * @return the correlation id used to route progression events to this saga instance
     */
    public String correlationId() {
        return correlationId;
    }

    /**
     * @return the mutable data bag backing this saga instance
     */
    public Map<String, Object> data() {
        return data;
    }

    /**
     * Reads a value from the data bag.
     *
     * @param key the key
     * @return the stored value, or {@code null} if absent
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Stores a value in the data bag.
     *
     * @param key   the key
     * @param value the value
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }
}
