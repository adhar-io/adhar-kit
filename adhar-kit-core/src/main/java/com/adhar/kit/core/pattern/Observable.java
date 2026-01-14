package com.adhar.kit.core.pattern;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Observer pattern implementation for event notification.
 *
 * <p>Enables loosely-coupled communication between objects.</p>
 *
 * <p><b>Example - Basic Usage:</b></p>
 * <pre>{@code
 * Observable<OrderEvent> orderEvents = new Observable<>();
 *
 * // Subscribe observers
 * orderEvents.subscribe(event -> emailService.sendNotification(event));
 * orderEvents.subscribe(event -> analyticsService.track(event));
 * orderEvents.subscribe(event -> warehouseService.processOrder(event));
 *
 * // Notify all observers
 * orderEvents.notifyObservers(new OrderCreatedEvent(order));
 * }</pre>
 *
 * <p><b>Example - Unsubscribe:</b></p>
 * <pre>{@code
 * Subscription subscription = orderEvents.subscribe(observer);
 *
 * // Later, unsubscribe
 * subscription.unsubscribe();
 * }</pre>
 *
 * @param <T> event type
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class Observable<T> {

    private final List<Observer<T>> observers = new ArrayList<>();

    /**
     * Subscribes an observer.
     *
     * @param observer the observer
     * @return subscription handle
     */
    public Subscription subscribe(Observer<T> observer) {
        observers.add(observer);
        log.debug("Observer subscribed. Total observers: {}", observers.size());
        return () -> unsubscribe(observer);
    }

    /**
     * Subscribes a consumer as observer.
     *
     * @param consumer the consumer
     * @return subscription handle
     */
    public Subscription subscribe(Consumer<T> consumer) {
        Observer<T> observer = consumer::accept;
        return subscribe(observer);
    }

    /**
     * Unsubscribes an observer.
     *
     * @param observer the observer to remove
     */
    public void unsubscribe(Observer<T> observer) {
        observers.remove(observer);
        log.debug("Observer unsubscribed. Remaining observers: {}", observers.size());
    }

    /**
     * Notifies all observers.
     *
     * @param event the event to notify
     */
    public void notifyObservers(T event) {
        log.debug("Notifying {} observers of event: {}", observers.size(), event.getClass().getSimpleName());
        for (Observer<T> observer : new ArrayList<>(observers)) {
            try {
                observer.onEvent(event);
            } catch (Exception e) {
                log.error("Observer failed to handle event: {}", event, e);
            }
        }
    }

    /**
     * Gets observer count.
     *
     * @return number of observers
     */
    public int getObserverCount() {
        return observers.size();
    }

    /**
     * Clears all observers.
     */
    public void clear() {
        observers.clear();
        log.debug("All observers cleared");
    }

    /**
     * Observer interface.
     *
     * @param <T> event type
     */
    @FunctionalInterface
    public interface Observer<T> {
        /**
         * Handles an event.
         *
         * @param event the event
         */
        void onEvent(T event);
    }

    /**
     * Subscription handle for unsubscribing.
     */
    @FunctionalInterface
    public interface Subscription {
        /**
         * Unsubscribes the observer.
         */
        void unsubscribe();
    }
}

