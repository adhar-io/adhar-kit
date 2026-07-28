package com.adhar.kit.health.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Bridges the {@link HealthTransitionListener} SPI onto Spring's application-event bus.
 *
 * <p>Registered as a transition listener on the
 * {@link com.adhar.kit.health.registry.HealthRegistry}, it republishes every transition
 * as a {@link HealthTransitionApplicationEvent} so ordinary Spring beans can observe
 * health changes with {@code @EventListener}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class SpringHealthEventPublisher implements HealthTransitionListener {

    private final ApplicationEventPublisher publisher;

    /**
     * Creates the publisher.
     *
     * @param publisher Spring application-event publisher
     */
    public SpringHealthEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onTransition(HealthTransition transition) {
        publisher.publishEvent(new HealthTransitionApplicationEvent(this, transition));
        if (log.isDebugEnabled()) {
            log.debug("Published health transition event for {}", transition.indicator());
        }
    }
}
