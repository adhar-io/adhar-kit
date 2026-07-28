package com.adhar.kit.health.event;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Framework-agnostic fan-out of {@link HealthTransition}s to live subscribers.
 *
 * <p>Registered as a {@link HealthTransitionListener} on the
 * {@link com.adhar.kit.health.registry.HealthRegistry}, this broadcaster turns the
 * callback SPI into a stream: any number of subscribers receive every transition.
 * It deliberately has no web or Spring dependency so it can be unit-tested and reused
 * by the SSE endpoint, an application-event bridge, or custom consumers.</p>
 *
 * <p>Subscriber callbacks are invoked synchronously on the health-check thread and
 * must be fast; exceptions thrown by a subscriber are logged and swallowed so one
 * misbehaving subscriber cannot break the others.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class HealthEventBroadcaster implements HealthTransitionListener {

    private final List<Consumer<HealthTransition>> subscribers = new CopyOnWriteArrayList<>();

    /**
     * Subscribes to the transition stream.
     *
     * @param subscriber callback invoked for every subsequent transition
     * @return a handle whose {@link Runnable#run()} removes the subscription (idempotent)
     */
    public Runnable subscribe(Consumer<HealthTransition> subscriber) {
        if (subscriber == null) {
            throw new IllegalArgumentException("subscriber must not be null");
        }
        subscribers.add(subscriber);
        log.debug("Health event subscriber added (count={})", subscribers.size());
        return () -> {
            if (subscribers.remove(subscriber)) {
                log.debug("Health event subscriber removed (count={})", subscribers.size());
            }
        };
    }

    /**
     * Gets the number of active subscribers.
     *
     * @return active subscriber count
     */
    public int subscriberCount() {
        return subscribers.size();
    }

    @Override
    public void onTransition(HealthTransition transition) {
        for (Consumer<HealthTransition> subscriber : subscribers) {
            try {
                subscriber.accept(transition);
            } catch (Exception e) {
                log.warn("Health event subscriber failed for transition {}", transition.indicator(), e);
            }
        }
    }
}
