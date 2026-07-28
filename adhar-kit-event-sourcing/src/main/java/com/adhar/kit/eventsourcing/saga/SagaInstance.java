package com.adhar.kit.eventsourcing.saga;

import java.util.HashMap;
import java.util.Map;

/**
 * Mutable persisted state of a single running saga.
 *
 * <p>{@code currentStepIndex} doubles as the count of completed steps: steps {@code [0,
 * currentStepIndex)} have run to completion, and the step at {@code currentStepIndex} is the one
 * currently executing or awaiting a progression event. On failure the {@link SagaManager}
 * compensates steps {@code currentStepIndex - 1 .. 0} in reverse.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class SagaInstance {

    private final String id;
    private final String sagaName;
    private final String correlationId;
    private int currentStepIndex;
    private SagaStatus status;
    private String awaitingEventType;
    private final Map<String, Object> data;

    public SagaInstance(String id, String sagaName, String correlationId,
                        int currentStepIndex, SagaStatus status, String awaitingEventType,
                        Map<String, Object> data) {
        this.id = id;
        this.sagaName = sagaName;
        this.correlationId = correlationId;
        this.currentStepIndex = currentStepIndex;
        this.status = status;
        this.awaitingEventType = awaitingEventType;
        this.data = data != null ? data : new HashMap<>();
    }

    /**
     * Creates a fresh instance positioned at the first step in {@link SagaStatus#RUNNING}.
     *
     * @param id            the unique instance id
     * @param sagaName      the definition name
     * @param correlationId the correlation id progression events are matched against
     * @param data          the initial data bag (may be {@code null})
     * @return a new running instance
     */
    public static SagaInstance start(String id, String sagaName, String correlationId, Map<String, Object> data) {
        return new SagaInstance(id, sagaName, correlationId, 0, SagaStatus.RUNNING, null,
                data != null ? new HashMap<>(data) : new HashMap<>());
    }

    public String getId() {
        return id;
    }

    public String getSagaName() {
        return sagaName;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public void setCurrentStepIndex(int currentStepIndex) {
        this.currentStepIndex = currentStepIndex;
    }

    public void advance() {
        this.currentStepIndex++;
    }

    public SagaStatus getStatus() {
        return status;
    }

    public void setStatus(SagaStatus status) {
        this.status = status;
    }

    public String getAwaitingEventType() {
        return awaitingEventType;
    }

    public void setAwaitingEventType(String awaitingEventType) {
        this.awaitingEventType = awaitingEventType;
    }

    public Map<String, Object> getData() {
        return data;
    }
}
