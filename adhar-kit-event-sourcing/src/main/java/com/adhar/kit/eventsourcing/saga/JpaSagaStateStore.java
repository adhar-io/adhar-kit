package com.adhar.kit.eventsourcing.saga;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JPA-backed {@link SagaStateStore} implementation.
 *
 * <p>Persists saga instances to the {@code saga_instances} table via
 * {@link SagaInstanceEntryRepository}, serializing the instance's data bag to JSON.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@ConditionalOnClass(EntityManager.class)
public class JpaSagaStateStore implements SagaStateStore {

    private static final TypeReference<Map<String, Object>> DATA_TYPE = new TypeReference<>() { };

    private final SagaInstanceEntryRepository repository;
    private final ObjectMapper objectMapper;

    public JpaSagaStateStore(SagaInstanceEntryRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(SagaInstance instance) {
        SagaInstanceEntry entry = new SagaInstanceEntry(
                instance.getId(),
                instance.getSagaName(),
                instance.getCorrelationId(),
                instance.getCurrentStepIndex(),
                instance.getStatus(),
                instance.getAwaitingEventType(),
                serializeData(instance.getData()));
        repository.save(entry);
    }

    @Override
    public Optional<SagaInstance> findById(String id) {
        return repository.findById(id).map(this::toInstance);
    }

    @Override
    public List<SagaInstance> findByStatus(SagaStatus status) {
        return repository.findByStatus(status).stream()
                .map(this::toInstance)
                .toList();
    }

    private SagaInstance toInstance(SagaInstanceEntry entry) {
        return new SagaInstance(
                entry.getId(),
                entry.getSagaName(),
                entry.getCorrelationId(),
                entry.getCurrentStepIndex(),
                entry.getStatus(),
                entry.getAwaitingEventType(),
                deserializeData(entry.getData()));
    }

    private String serializeData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize saga data", ex);
        }
    }

    private Map<String, Object> deserializeData(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, DATA_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to deserialize saga data", ex);
        }
    }
}
