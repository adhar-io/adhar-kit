package com.adhar.kit.persistence.outbox;

import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;

/**
 * Default {@link OutboxRelay} that republishes each due {@link OutboxEvent} through Spring's
 * {@link ApplicationEventPublisher} -- the module's original (pre-1.3.0) behavior.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class ApplicationEventOutboxRelay implements OutboxRelay {

    private final ApplicationEventPublisher eventPublisher;

    public ApplicationEventOutboxRelay(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    @Override
    public void relay(OutboxEvent event) {
        eventPublisher.publishEvent(event);
    }
}
