package com.adhar.kit.health.event;

/**
 * SPI invoked whenever a health indicator changes status.
 *
 * <p>Register implementations with
 * {@link com.adhar.kit.health.registry.HealthRegistry#addTransitionListener(HealthTransitionListener)}.
 * Listeners are invoked synchronously after each health check that detects a status
 * change; implementations must be fast and must not throw (exceptions are logged
 * and swallowed by the registry).</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * registry.addTransitionListener(transition ->
 *     log.warn("{} went {} -> {}", transition.indicator(), transition.from(), transition.to()));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@FunctionalInterface
public interface HealthTransitionListener {

    /**
     * Called when an indicator's status changes.
     *
     * @param transition the observed transition
     */
    void onTransition(HealthTransition transition);
}
