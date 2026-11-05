package com.adhar.kit.batch.listener;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.function.Consumer;

/**
 * Calls every {@link BatchRunObserver}, isolating the run from an observer's failure.
 *
 * @author Adhar Platform Team
 * @since 0.1.0
 */
@Slf4j
final class BatchRunObservers {

    private final List<BatchRunObserver> observers;

    BatchRunObservers(List<BatchRunObserver> observers) {
        this.observers = observers == null ? List.of() : List.copyOf(observers);
    }

    void notify(String event, Consumer<BatchRunObserver> call) {
        for (BatchRunObserver observer : observers) {
            try {
                call.accept(observer);
            } catch (RuntimeException ex) {
                log.warn("Batch observer {} failed on {}: {}", observer.getClass().getName(), event, ex.getMessage());
            }
        }
    }

    boolean isEmpty() {
        return observers.isEmpty();
    }
}
