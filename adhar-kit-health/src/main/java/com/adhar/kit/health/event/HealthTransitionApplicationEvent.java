package com.adhar.kit.health.event;

import org.springframework.context.ApplicationEvent;

/**
 * Spring {@link ApplicationEvent} published whenever a health indicator changes status.
 *
 * <p>Published by {@link SpringHealthEventPublisher} so application code can react to
 * health transitions with an ordinary {@code @EventListener} or
 * {@link org.springframework.context.ApplicationListener} instead of implementing the
 * {@link HealthTransitionListener} SPI directly.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @EventListener
 * public void onHealthChange(HealthTransitionApplicationEvent event) {
 *     HealthTransition t = event.getTransition();
 *     log.warn("{} went {} -> {}", t.indicator(), t.from(), t.to());
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
public class HealthTransitionApplicationEvent extends ApplicationEvent {

    private final transient HealthTransition transition;

    /**
     * Creates the event.
     *
     * @param source     the component publishing the event (never {@code null})
     * @param transition the observed transition
     */
    public HealthTransitionApplicationEvent(Object source, HealthTransition transition) {
        super(source);
        this.transition = transition;
    }

    /**
     * Gets the health transition carried by this event.
     *
     * @return the transition
     */
    public HealthTransition getTransition() {
        return transition;
    }
}
