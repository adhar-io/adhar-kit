package com.adhar.kit.eventsourcing.saga;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link SagaStateStore} implementation intended for development, testing and single-node
 * deployments.
 *
 * <p>Instances are copied on save and on read so that callers cannot mutate stored state without an
 * explicit {@link #save} call, matching the isolation a real persistence layer provides.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class InMemorySagaStateStore implements SagaStateStore {

    private final ConcurrentMap<String, SagaInstance> instances = new ConcurrentHashMap<>();

    @Override
    public void save(SagaInstance instance) {
        instances.put(instance.getId(), copy(instance));
    }

    @Override
    public Optional<SagaInstance> findById(String id) {
        return Optional.ofNullable(instances.get(id)).map(this::copy);
    }

    @Override
    public List<SagaInstance> findByStatus(SagaStatus status) {
        return instances.values().stream()
                .filter(instance -> instance.getStatus() == status)
                .map(this::copy)
                .toList();
    }

    private SagaInstance copy(SagaInstance source) {
        return new SagaInstance(
                source.getId(),
                source.getSagaName(),
                source.getCorrelationId(),
                source.getCurrentStepIndex(),
                source.getStatus(),
                source.getAwaitingEventType(),
                new HashMap<>(source.getData()));
    }
}
