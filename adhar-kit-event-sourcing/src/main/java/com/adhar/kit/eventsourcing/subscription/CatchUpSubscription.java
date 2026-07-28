package com.adhar.kit.eventsourcing.subscription;

import com.adhar.kit.eventsourcing.bus.EventBus;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.store.EventStore;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A catch-up subscription that first replays historical events from an {@link EventStore} starting
 * at a caller-supplied checkpoint, then transparently switches to live events delivered by the
 * {@link EventBus} without dropping or duplicating events across the boundary.
 *
 * <p>The classic gap problem is that live events published while the historical replay is still in
 * progress would otherwise be missed. This implementation avoids it by subscribing to the bus
 * <em>before</em> replay begins and buffering any live events that arrive during catch-up. Once the
 * historical replay drains, the buffer is processed in arrival order and the subscription flips to
 * fully live. Events already seen during replay are de-duplicated by {@code eventId} so an event
 * that was both persisted (and replayed) and delivered live is handled exactly once.</p>
 *
 * <p>The subscription tracks its {@link #getPosition() position} as the number of events it has
 * handled since {@link #start()}, allowing callers to persist and later resume from a checkpoint.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class CatchUpSubscription {

    /**
     * Lifecycle phases of a catch-up subscription.
     */
    public enum State {
        /** Created but {@link #start()} not yet called. */
        NEW,
        /** Replaying historical events; live events are being buffered. */
        CATCHING_UP,
        /** Fully caught up; events are dispatched directly as they arrive. */
        LIVE,
        /** Stopped; no further events are dispatched. */
        STOPPED
    }

    private final EventStore eventStore;
    private final EventBus eventBus;
    private final List<String> eventTypes;
    private final Consumer<DomainEvent> handler;

    private final Object lock = new Object();
    private final Deque<DomainEvent> liveBuffer = new ArrayDeque<>();
    private final Set<String> replayedEventIds = new HashSet<>();

    private volatile State state = State.NEW;
    private long position;

    /**
     * Creates a catch-up subscription.
     *
     * @param eventStore the store historical events are replayed from
     * @param eventBus   the bus live events are consumed from
     * @param eventTypes the event types this subscription is interested in
     * @param handler    the handler invoked for every replayed and live event, in order
     */
    public CatchUpSubscription(EventStore eventStore, EventBus eventBus, Collection<String> eventTypes,
                               Consumer<DomainEvent> handler) {
        this.eventStore = eventStore;
        this.eventBus = eventBus;
        this.eventTypes = List.copyOf(eventTypes);
        this.handler = handler;
    }

    /**
     * Starts the subscription from position zero (the beginning of the stream).
     */
    public void start() {
        start(0L);
    }

    /**
     * Starts the subscription, skipping the first {@code fromPosition} events of the interested
     * stream before dispatching begins. Live events are buffered from the moment this method is
     * invoked so none are lost while historical events are replayed.
     *
     * @param fromPosition the checkpoint position (number of already-processed events) to resume from
     * @throws IllegalStateException if the subscription has already been started
     */
    public void start(long fromPosition) {
        synchronized (lock) {
            if (state != State.NEW) {
                throw new IllegalStateException("Subscription already started (state=" + state + ")");
            }
            state = State.CATCHING_UP;
        }

        // Subscribe first so live events arriving during replay are captured, not dropped.
        for (String eventType : eventTypes) {
            eventBus.subscribe(eventType, this::onLiveEvent);
        }

        long skipped = 0;
        long handled = 0;
        for (DomainEvent event : eventStore.getAllEvents()) {
            if (!eventTypes.contains(event.eventType())) {
                continue;
            }
            if (skipped < fromPosition) {
                skipped++;
                replayedEventIds.add(event.eventId());
                continue;
            }
            dispatch(event);
            replayedEventIds.add(event.eventId());
            handled++;
        }
        position = fromPosition + handled;
        log.debug("Catch-up replay complete: skipped {}, handled {}, position {}", skipped, handled, position);

        drainAndGoLive();
    }

    private void onLiveEvent(DomainEvent event) {
        synchronized (lock) {
            if (state == State.STOPPED) {
                return;
            }
            if (state == State.CATCHING_UP) {
                liveBuffer.addLast(event);
                return;
            }
            // LIVE: dispatch directly, skipping anything already seen during replay.
            if (replayedEventIds.remove(event.eventId())) {
                return;
            }
            dispatch(event);
            position++;
        }
    }

    private void drainAndGoLive() {
        synchronized (lock) {
            if (state != State.CATCHING_UP) {
                return;
            }
            while (!liveBuffer.isEmpty()) {
                DomainEvent buffered = liveBuffer.pollFirst();
                if (replayedEventIds.remove(buffered.eventId())) {
                    continue;
                }
                dispatch(buffered);
                position++;
            }
            replayedEventIds.clear();
            state = State.LIVE;
            log.debug("Catch-up subscription is now LIVE at position {}", position);
        }
    }

    private void dispatch(DomainEvent event) {
        try {
            handler.accept(event);
        } catch (Exception ex) {
            log.error("Catch-up subscription handler failed for event '{}' (aggregate '{}'): {}",
                    event.eventType(), event.aggregateId(), ex.getMessage(), ex);
        }
    }

    /**
     * Stops the subscription. Buffered and subsequent live events are ignored. The underlying bus
     * subscription cannot be removed (the {@link EventBus} API is subscribe-only), so a guard flag
     * is used to make further dispatch a no-op.
     */
    public void stop() {
        synchronized (lock) {
            state = State.STOPPED;
            liveBuffer.clear();
        }
    }

    /**
     * @return the number of events this subscription has handled since it started
     */
    public long getPosition() {
        synchronized (lock) {
            return position;
        }
    }

    /**
     * @return the current lifecycle state
     */
    public State getState() {
        return state;
    }
}
