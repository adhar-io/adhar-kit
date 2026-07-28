package com.adhar.kit.eventsourcing.saga;

import java.util.ArrayList;
import java.util.List;

/**
 * An ordered, named definition of the steps that make up an orchestration saga.
 *
 * <p>A definition may optionally declare a {@link #startEventType() start event type}; when the
 * {@link SagaManager} is wired to an {@link com.adhar.kit.eventsourcing.bus.EventBus} it starts a
 * new instance of this saga each time such an event is observed.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class SagaDefinition {

    private final String name;
    private final List<SagaStep> steps;
    private final String startEventType;

    private SagaDefinition(Builder builder) {
        this.name = builder.name;
        this.steps = List.copyOf(builder.steps);
        this.startEventType = builder.startEventType;
    }

    /**
     * Starts building a saga definition with the given name.
     *
     * @param name the saga name (must be unique within a {@link SagaManager})
     * @return a builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public List<SagaStep> steps() {
        return steps;
    }

    public SagaStep step(int index) {
        return steps.get(index);
    }

    public int stepCount() {
        return steps.size();
    }

    /**
     * @return the domain event type that triggers a new instance of this saga, or {@code null}
     */
    public String startEventType() {
        return startEventType;
    }

    /**
     * Fluent builder for {@link SagaDefinition}.
     */
    public static final class Builder {
        private final String name;
        private final List<SagaStep> steps = new ArrayList<>();
        private String startEventType;

        private Builder(String name) {
            this.name = name;
        }

        public Builder step(SagaStep step) {
            this.steps.add(step);
            return this;
        }

        public Builder startEventType(String startEventType) {
            this.startEventType = startEventType;
            return this;
        }

        public SagaDefinition build() {
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("Saga definition '" + name + "' must declare at least one step");
            }
            return new SagaDefinition(this);
        }
    }
}
