package com.adhar.kit.eventsourcing.saga;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Orchestration-style saga manager.
 *
 * <p>Executes {@link SagaDefinition}s step by step, persisting {@link SagaInstance} state via a
 * {@link SagaStateStore} after every transition. Synchronous steps advance as soon as their action
 * returns; asynchronous steps run their action and then pause until a matching domain event arrives
 * (routed by correlation id). When any step's action throws, or an asynchronous step receives its
 * declared failure event, the manager compensates every completed step in reverse order.</p>
 *
 * <p>When constructed with an {@link EventBus}, the manager subscribes to the progression, failure
 * and start event types of each registered definition so sagas advance in an event-driven fashion.
 * It can equally be driven directly via {@link #start} and {@link #onEvent} in tests.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class SagaManager {

    private final SagaStateStore stateStore;
    private final EventBus eventBus;
    private final ConcurrentMap<String, SagaDefinition> definitions = new ConcurrentHashMap<>();

    public SagaManager(SagaStateStore stateStore) {
        this(stateStore, null);
    }

    public SagaManager(SagaStateStore stateStore, EventBus eventBus) {
        this.stateStore = stateStore;
        this.eventBus = eventBus;
    }

    /**
     * Registers a saga definition. If an {@link EventBus} was supplied, subscribes the manager to
     * every event type the definition reacts to (start, per-step await and failure events).
     *
     * @param definition the definition to register
     */
    public void register(SagaDefinition definition) {
        definitions.put(definition.name(), definition);
        if (eventBus != null) {
            subscribeEventTypes(definition);
        }
        log.debug("Registered saga definition '{}' with {} steps", definition.name(), definition.stepCount());
    }

    private void subscribeEventTypes(SagaDefinition definition) {
        if (definition.startEventType() != null) {
            eventBus.subscribe(definition.startEventType(), this::onEvent);
        }
        for (SagaStep step : definition.steps()) {
            if (step.awaitEventType() != null) {
                eventBus.subscribe(step.awaitEventType(), this::onEvent);
            }
            if (step.failureEventType() != null) {
                eventBus.subscribe(step.failureEventType(), this::onEvent);
            }
        }
    }

    /**
     * Starts a new instance of the named saga and drives it until it either completes, fails,
     * compensates, or pauses on an asynchronous step.
     *
     * @param sagaName      the registered definition name
     * @param correlationId the correlation id used to route progression events (may be {@code null})
     * @param initialData   the initial data bag (may be {@code null})
     * @return the resulting saga instance
     * @throws IllegalArgumentException if no definition is registered under {@code sagaName}
     */
    public SagaInstance start(String sagaName, String correlationId, Map<String, Object> initialData) {
        SagaDefinition definition = requireDefinition(sagaName);
        SagaInstance instance = SagaInstance.start(UUID.randomUUID().toString(), sagaName, correlationId, initialData);
        stateStore.save(instance);
        log.debug("Started saga '{}' instance '{}' (correlationId={})", sagaName, instance.getId(), correlationId);
        executeCurrentStep(definition, instance);
        return instance;
    }

    /**
     * Routes a domain event to the sagas that react to it: starting new instances for definitions
     * whose start event type matches, and advancing or compensating running instances that are
     * awaiting a progression or failure event correlated to the event's aggregate id.
     *
     * @param event the domain event
     */
    public void onEvent(DomainEvent event) {
        for (SagaDefinition definition : definitions.values()) {
            if (event.eventType().equals(definition.startEventType())) {
                start(definition.name(), event.aggregateId(), Map.of());
            }
        }
        for (SagaInstance instance : stateStore.findByStatus(SagaStatus.RUNNING)) {
            if (instance.getAwaitingEventType() == null) {
                continue;
            }
            if (instance.getCorrelationId() != null && !instance.getCorrelationId().equals(event.aggregateId())) {
                continue;
            }
            SagaDefinition definition = definitions.get(instance.getSagaName());
            if (definition == null) {
                continue;
            }
            SagaStep step = definition.step(instance.getCurrentStepIndex());
            if (event.eventType().equals(step.awaitEventType())) {
                instance.setAwaitingEventType(null);
                instance.advance();
                stateStore.save(instance);
                executeCurrentStep(definition, instance);
            } else if (event.eventType().equals(step.failureEventType())) {
                log.debug("Saga '{}' instance '{}' received failure event '{}' on step '{}'",
                        instance.getSagaName(), instance.getId(), event.eventType(), step.name());
                compensate(definition, instance);
            }
        }
    }

    private void executeCurrentStep(SagaDefinition definition, SagaInstance instance) {
        if (instance.getCurrentStepIndex() >= definition.stepCount()) {
            complete(instance);
            return;
        }
        SagaStep step = definition.step(instance.getCurrentStepIndex());
        SagaContext context = new SagaContext(instance.getCorrelationId(), instance.getData());
        try {
            step.action().accept(context);
        } catch (Exception ex) {
            log.error("Saga '{}' instance '{}' failed executing step '{}': {}",
                    instance.getSagaName(), instance.getId(), step.name(), ex.getMessage(), ex);
            compensate(definition, instance);
            return;
        }

        if (step.isAsynchronous()) {
            instance.setAwaitingEventType(step.awaitEventType());
            stateStore.save(instance);
            log.debug("Saga '{}' instance '{}' awaiting event '{}' after step '{}'",
                    instance.getSagaName(), instance.getId(), step.awaitEventType(), step.name());
            return;
        }

        instance.advance();
        stateStore.save(instance);
        executeCurrentStep(definition, instance);
    }

    private void complete(SagaInstance instance) {
        instance.setStatus(SagaStatus.COMPLETED);
        instance.setAwaitingEventType(null);
        stateStore.save(instance);
        log.debug("Saga '{}' instance '{}' completed", instance.getSagaName(), instance.getId());
    }

    private void compensate(SagaDefinition definition, SagaInstance instance) {
        instance.setStatus(SagaStatus.COMPENSATING);
        instance.setAwaitingEventType(null);
        stateStore.save(instance);

        SagaContext context = new SagaContext(instance.getCorrelationId(), instance.getData());
        boolean allCompensated = true;
        for (int i = instance.getCurrentStepIndex() - 1; i >= 0; i--) {
            SagaStep step = definition.step(i);
            try {
                step.compensation().accept(context);
            } catch (Exception ex) {
                allCompensated = false;
                log.error("Saga '{}' instance '{}' failed compensating step '{}': {}",
                        instance.getSagaName(), instance.getId(), step.name(), ex.getMessage(), ex);
            }
        }
        instance.setStatus(allCompensated ? SagaStatus.COMPENSATED : SagaStatus.FAILED);
        stateStore.save(instance);
        log.debug("Saga '{}' instance '{}' finished compensation with status {}",
                instance.getSagaName(), instance.getId(), instance.getStatus());
    }

    private SagaDefinition requireDefinition(String sagaName) {
        SagaDefinition definition = definitions.get(sagaName);
        if (definition == null) {
            throw new IllegalArgumentException("No saga definition registered with name: " + sagaName);
        }
        return definition;
    }
}
