package com.adhar.kit.health.history;

import com.adhar.kit.health.event.HealthTransition;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Bounded ring buffer of {@link HealthTransition}s with a flapping detector.
 *
 * <p>Keeps the most recent {@code capacity} transitions across all indicators.
 * When the buffer is full the oldest transition is evicted.</p>
 *
 * <p><b>Flapping detection:</b> an indicator is considered flapping when it recorded
 * at least {@code threshold} transitions within the given time window — a strong
 * signal of an unstable dependency that alternates between UP and DOWN.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * HealthHistory history = registry.getHistory();
 * List<HealthTransition> databaseChanges = history.getTransitions("database");
 * boolean unstable = history.isFlapping("database", 5, Duration.ofMinutes(1));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public class HealthHistory {

    /** Default maximum number of retained transitions. */
    public static final int DEFAULT_CAPACITY = 100;

    private final Deque<HealthTransition> transitions = new ArrayDeque<>();
    private int capacity;

    /**
     * Creates a history with the default capacity.
     */
    public HealthHistory() {
        this(DEFAULT_CAPACITY);
    }

    /**
     * Creates a history with the given capacity.
     *
     * @param capacity maximum number of retained transitions (must be positive)
     */
    public HealthHistory(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
    }

    /**
     * Records a transition, evicting the oldest entry when full.
     *
     * @param transition transition to record
     */
    public synchronized void record(HealthTransition transition) {
        if (transition == null) {
            return;
        }
        while (transitions.size() >= capacity) {
            transitions.pollFirst();
        }
        transitions.addLast(transition);
    }

    /**
     * Gets all retained transitions, oldest first.
     *
     * @return snapshot list of transitions
     */
    public synchronized List<HealthTransition> getTransitions() {
        return new ArrayList<>(transitions);
    }

    /**
     * Gets retained transitions for a single indicator, oldest first.
     *
     * @param indicator indicator name
     * @return snapshot list of transitions for the indicator
     */
    public synchronized List<HealthTransition> getTransitions(String indicator) {
        List<HealthTransition> result = new ArrayList<>();
        for (HealthTransition transition : transitions) {
            if (transition.indicator().equals(indicator)) {
                result.add(transition);
            }
        }
        return result;
    }

    /**
     * Detects whether an indicator is flapping.
     *
     * @param indicator indicator name
     * @param threshold minimum number of transitions to consider flapping
     * @param window    look-back window
     * @return true when the indicator recorded at least {@code threshold} transitions
     *         within {@code window}
     */
    public synchronized boolean isFlapping(String indicator, int threshold, Duration window) {
        Instant cutoff = Instant.now().minus(window);
        int count = 0;
        for (HealthTransition transition : transitions) {
            if (transition.indicator().equals(indicator) && !transition.timestamp().isBefore(cutoff)) {
                count++;
                if (count >= threshold) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets the current number of retained transitions.
     *
     * @return retained transition count
     */
    public synchronized int size() {
        return transitions.size();
    }

    /**
     * Gets the configured capacity.
     *
     * @return maximum number of retained transitions
     */
    public synchronized int getCapacity() {
        return capacity;
    }

    /**
     * Changes the capacity, trimming the oldest entries if necessary.
     *
     * @param capacity new capacity (must be positive)
     */
    public synchronized void setCapacity(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        while (transitions.size() > capacity) {
            transitions.pollFirst();
        }
    }

    /**
     * Removes all retained transitions.
     */
    public synchronized void clear() {
        transitions.clear();
    }
}
