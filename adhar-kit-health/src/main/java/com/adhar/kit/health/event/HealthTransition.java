package com.adhar.kit.health.event;

import com.adhar.kit.health.model.Health;

import java.time.Instant;

/**
 * Immutable record of a health status transition for a single indicator.
 *
 * <p>A transition is recorded whenever an indicator's observed status differs from
 * the previously observed status. The very first observation of an indicator is
 * recorded with a {@code null} {@code from} status.</p>
 *
 * @param indicator name of the indicator that transitioned
 * @param from      previous status ({@code null} for the first observation)
 * @param to        new status
 * @param timestamp when the transition was observed
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
public record HealthTransition(
        String indicator,
        Health.Status from,
        Health.Status to,
        Instant timestamp) {

    /**
     * Checks whether this is the first observation of the indicator.
     *
     * @return true when no previous status existed
     */
    public boolean isInitial() {
        return from == null;
    }
}
