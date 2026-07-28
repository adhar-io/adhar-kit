package com.adhar.kit.dapr.outbox;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.state.StateRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A small transactional-outbox pub/sub relay backed by the Dapr state store.
 *
 * <p>{@link #append} durably persists an event (via the ETag-aware {@link StateRepository}) and
 * records its id in a pending index. A scheduled {@link #relay()} pass then publishes each
 * pending event through Dapr pub/sub, marking it {@link OutboxStatus#PUBLISHED} on success or
 * retrying on failure; once {@code maxAttempts} is exhausted the event is parked as
 * {@link OutboxStatus#DEAD}.</p>
 *
 * <p>Because persistence and relay are decoupled, a producer can atomically write business state
 * and the outbox event, and delivery to the broker is retried independently of the request.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class OutboxPublisher {

    static final String EVENT_KEY_PREFIX = "outbox:evt:";
    static final String INDEX_KEY = "outbox:index";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final DaprFacade daprFacade;
    private final String defaultPubsub;
    private final int maxAttempts;
    private final StateRepository<OutboxEvent> eventRepo;
    private final StateRepository<OutboxIndex> indexRepo;

    public OutboxPublisher(DaprFacade daprFacade, String stateStore, String defaultPubsub) {
        this(daprFacade, stateStore, defaultPubsub, DEFAULT_MAX_ATTEMPTS);
    }

    public OutboxPublisher(DaprFacade daprFacade, String stateStore, String defaultPubsub, int maxAttempts) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.defaultPubsub = Objects.requireNonNull(defaultPubsub, "defaultPubsub must not be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.eventRepo = new StateRepository<>(daprFacade, stateStore, OutboxEvent.class);
        this.indexRepo = new StateRepository<>(daprFacade, stateStore, OutboxIndex.class);
    }

    /**
     * Persists an event for the default pub/sub component and enqueues it for relay.
     *
     * @return the generated event id
     */
    public String append(String topic, Object payload) {
        return append(defaultPubsub, topic, payload);
    }

    /**
     * Persists an event and enqueues it for relay.
     *
     * @param pubsubName target pub/sub component
     * @param topic      target topic
     * @param payload    event payload (serialized as JSON)
     * @return the generated event id
     */
    public String append(String pubsubName, String topic, Object payload) {
        Objects.requireNonNull(pubsubName, "pubsubName must not be null");
        Objects.requireNonNull(topic, "topic must not be null");
        String id = UUID.randomUUID().toString();
        OutboxEvent event = new OutboxEvent(id, pubsubName, topic, payload,
            OutboxStatus.PENDING, 0, System.currentTimeMillis(), null);
        eventRepo.save(EVENT_KEY_PREFIX + id, event);
        indexRepo.update(INDEX_KEY, current -> {
            OutboxIndex index = current != null ? current : new OutboxIndex();
            index.getPendingIds().add(id);
            return index;
        });
        log.debug("Appended outbox event: id={}, pubsub={}, topic={}", id, pubsubName, topic);
        return id;
    }

    /**
     * Publishes all currently pending events. Safe to call repeatedly (e.g. on a schedule).
     *
     * @return a summary of what happened in this pass
     */
    public OutboxRelayResult relay() {
        Optional<OutboxIndex> maybeIndex = indexRepo.find(INDEX_KEY);
        if (maybeIndex.isEmpty() || maybeIndex.get().getPendingIds().isEmpty()) {
            return new OutboxRelayResult(0, 0, 0);
        }
        List<String> pending = new ArrayList<>(maybeIndex.get().getPendingIds());
        int published = 0;
        int retried = 0;
        int dead = 0;
        for (String id : pending) {
            OutboxEvent event = eventRepo.find(EVENT_KEY_PREFIX + id).orElse(null);
            if (event == null) {
                // Orphaned index entry - drop it.
                removeFromIndex(id);
                continue;
            }
            try {
                daprFacade.publishEvent(event.getPubsubName(), event.getTopic(), event.getPayload());
                event.setStatus(OutboxStatus.PUBLISHED);
                event.setLastError(null);
                eventRepo.save(EVENT_KEY_PREFIX + id, event);
                removeFromIndex(id);
                published++;
                log.debug("Relayed outbox event: id={}", id);
            } catch (Exception e) {
                event.setAttempts(event.getAttempts() + 1);
                event.setLastError(e.getMessage());
                if (event.getAttempts() >= maxAttempts) {
                    event.setStatus(OutboxStatus.DEAD);
                    eventRepo.save(EVENT_KEY_PREFIX + id, event);
                    removeFromIndex(id);
                    dead++;
                    log.warn("Outbox event {} moved to DEAD after {} attempts: {}",
                        id, event.getAttempts(), e.getMessage());
                } else {
                    eventRepo.save(EVENT_KEY_PREFIX + id, event);
                    retried++;
                    log.debug("Outbox event {} publish failed (attempt {}/{}); will retry",
                        id, event.getAttempts(), maxAttempts);
                }
            }
        }
        return new OutboxRelayResult(published, retried, dead);
    }

    /**
     * Looks up a stored event by id (regardless of status).
     */
    public Optional<OutboxEvent> getEvent(String id) {
        return eventRepo.find(EVENT_KEY_PREFIX + id);
    }

    private void removeFromIndex(String id) {
        indexRepo.update(INDEX_KEY, current -> {
            if (current != null) {
                current.getPendingIds().remove(id);
            }
            return current != null ? current : new OutboxIndex();
        });
    }
}
