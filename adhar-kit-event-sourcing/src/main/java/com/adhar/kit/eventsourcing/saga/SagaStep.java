package com.adhar.kit.eventsourcing.saga;

import java.util.function.Consumer;

/**
 * A single step of a {@link SagaDefinition}, pairing a forward {@code action} with a
 * {@code compensation} that undoes it.
 *
 * <p>A step may be <em>synchronous</em> — completing as soon as its action returns — or
 * <em>asynchronous</em>, in which case it declares an {@link #awaitEventType() await event type}
 * and the saga pauses after running the action until the orchestrator receives that event. An
 * optional {@link #failureEventType() failure event type} lets an asynchronous step trigger
 * compensation in response to a domain event rather than an exception.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class SagaStep {

    private final String name;
    private final Consumer<SagaContext> action;
    private final Consumer<SagaContext> compensation;
    private final String awaitEventType;
    private final String failureEventType;

    private SagaStep(Builder builder) {
        this.name = builder.name;
        this.action = builder.action;
        this.compensation = builder.compensation;
        this.awaitEventType = builder.awaitEventType;
        this.failureEventType = builder.failureEventType;
    }

    /**
     * Creates a synchronous step with an action and a compensation.
     *
     * @param name         the step name
     * @param action       the forward action
     * @param compensation the compensating action
     * @return a new step
     */
    public static SagaStep of(String name, Consumer<SagaContext> action, Consumer<SagaContext> compensation) {
        return builder(name).action(action).compensation(compensation).build();
    }

    /**
     * Starts building a step with the given name.
     *
     * @param name the step name
     * @return a builder
     */
    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public Consumer<SagaContext> action() {
        return action;
    }

    public Consumer<SagaContext> compensation() {
        return compensation;
    }

    /**
     * @return the domain event type that advances this step, or {@code null} for a synchronous step
     */
    public String awaitEventType() {
        return awaitEventType;
    }

    /**
     * @return the domain event type that triggers compensation for this step, or {@code null}
     */
    public String failureEventType() {
        return failureEventType;
    }

    /**
     * @return {@code true} if this step waits for a domain event before advancing
     */
    public boolean isAsynchronous() {
        return awaitEventType != null;
    }

    /**
     * Fluent builder for {@link SagaStep}.
     */
    public static final class Builder {
        private final String name;
        private Consumer<SagaContext> action = ctx -> { };
        private Consumer<SagaContext> compensation = ctx -> { };
        private String awaitEventType;
        private String failureEventType;

        private Builder(String name) {
            this.name = name;
        }

        public Builder action(Consumer<SagaContext> action) {
            this.action = action;
            return this;
        }

        public Builder compensation(Consumer<SagaContext> compensation) {
            this.compensation = compensation;
            return this;
        }

        public Builder awaitEventType(String awaitEventType) {
            this.awaitEventType = awaitEventType;
            return this;
        }

        public Builder failureEventType(String failureEventType) {
            this.failureEventType = failureEventType;
            return this;
        }

        public SagaStep build() {
            return new SagaStep(this);
        }
    }
}
