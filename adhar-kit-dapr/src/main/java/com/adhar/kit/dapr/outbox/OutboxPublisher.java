package com.adhar.kit.dapr.outbox;

import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.api.StateWithETag;
import com.adhar.kit.dapr.state.OptimisticConcurrencyException;
import com.adhar.kit.dapr.state.StateRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A small transactional-outbox pub/sub relay backed by the Dapr state store.
 *
 * <p>{@link #append} writes the event and its entry in the pending index in <em>one</em> state
 * transaction (the index guarded by its ETag, so concurrent appends cannot lose each other's
 * entries). A scheduled {@link #relay()} pass then publishes each pending event through Dapr
 * pub/sub, marking it {@link OutboxStatus#PUBLISHED} on success or retrying on failure; once
 * {@code maxAttempts} is exhausted the event is parked as {@link OutboxStatus#DEAD}.</p>
 *
 * <p>The relay takes a distributed lock first, so with several replicas one of them relays a pass
 * and events are not published once per replica. Without a usable lock store it relays unguarded
 * and says so once.</p>
 *
 * <p>When the state store itself is configured with Dapr's transactional outbox
 * ({@code outboxPublishPubsub}/{@code outboxPublishTopic}), prefer writing business state and the
 * event in one {@link DaprFacade#executeStateTransaction} instead: Dapr then publishes atomically
 * with the commit and no relay is needed.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class OutboxPublisher {

    static final String EVENT_KEY_PREFIX = "outbox:evt:";
    static final String INDEX_KEY = "outbox:index";
    static final String RELAY_LOCK = "outbox:relay";
    private static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int INDEX_RETRIES = 5;
    private static final int RELAY_LOCK_SECONDS = 60;

    private final DaprFacade daprFacade;
    private final String stateStore;
    private final String defaultPubsub;
    private final int maxAttempts;
    private final String lockStore;
    private final StateRepository<OutboxEvent> eventRepo;
    private final StateRepository<OutboxIndex> indexRepo;
    private final AtomicBoolean unguardedLogged = new AtomicBoolean();

    public OutboxPublisher(DaprFacade daprFacade, String stateStore, String defaultPubsub) {
        this(daprFacade, stateStore, defaultPubsub, DEFAULT_MAX_ATTEMPTS);
    }

    public OutboxPublisher(DaprFacade daprFacade, String stateStore, String defaultPubsub, int maxAttempts) {
        this(daprFacade, stateStore, defaultPubsub, maxAttempts, null);
    }

    /**
     * @param daprFacade    the facade
     * @param stateStore    where events and the index live
     * @param defaultPubsub the pub/sub component of {@link #append(String, Object)}
     * @param maxAttempts   publish attempts before an event is parked as DEAD
     * @param lockStore     the lock store guarding the relay, or {@code null} to relay unguarded
     */
    public OutboxPublisher(DaprFacade daprFacade, String stateStore, String defaultPubsub, int maxAttempts,
                           String lockStore) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.stateStore = Objects.requireNonNull(stateStore, "stateStore must not be null");
        this.defaultPubsub = Objects.requireNonNull(defaultPubsub, "defaultPubsub must not be null");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        this.maxAttempts = maxAttempts;
        this.lockStore = lockStore == null || lockStore.isBlank() ? null : lockStore;
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
        for (int attempt = 1; attempt <= INDEX_RETRIES; attempt++) {
            StateWithETag<OutboxIndex> current = indexRepo.findWithETag(INDEX_KEY);
            OutboxIndex index = current.getValue() != null ? current.getValue() : new OutboxIndex();
            index.getPendingIds().add(id);
            try {
                // One transaction: the event never exists without its index entry, or vice versa.
                daprFacade.executeStateTransaction(stateStore, List.of(
                    StateOperation.upsert(EVENT_KEY_PREFIX + id, event),
                    StateOperation.conditionalUpsert(INDEX_KEY, index, current.getEtag())));
                log.debug("Appended outbox event: id={}, pubsub={}, topic={}", id, pubsubName, topic);
                return id;
            } catch (DaprError e) {
                if (!isConflict(e)) {
                    throw e;
                }
                log.debug("Outbox index changed concurrently (attempt {}/{}); retrying", attempt, INDEX_RETRIES);
            }
        }
        throw new OptimisticConcurrencyException("Failed to append outbox event after " + INDEX_RETRIES
            + " attempts due to concurrent appends");
    }

    /**
     * Publishes all currently pending events. Safe to call repeatedly (e.g. on a schedule).
     *
     * @return a summary of what happened in this pass
     */
    public OutboxRelayResult relay() {
        String owner = lockStore == null ? null : "outbox-relay-" + UUID.randomUUID();
        if (owner != null && !acquireRelayLock(owner)) {
            return new OutboxRelayResult(0, 0, 0);
        }
        try {
            return relayPending();
        } finally {
            if (owner != null) {
                releaseRelayLock(owner);
            }
        }
    }

    private boolean acquireRelayLock(String owner) {
        try {
            return daprFacade.tryLock(lockStore, RELAY_LOCK, owner, RELAY_LOCK_SECONDS);
        } catch (RuntimeException e) {
            if (unguardedLogged.compareAndSet(false, true)) {
                log.warn("Outbox relay lock store '{}' is unusable ({}); relaying without a lock, so several replicas "
                    + "may publish the same event", lockStore, e.getMessage());
            }
            return true;
        }
    }

    private void releaseRelayLock(String owner) {
        try {
            daprFacade.unlock(lockStore, RELAY_LOCK, owner);
        } catch (RuntimeException e) {
            log.debug("Releasing the outbox relay lock failed (it expires on its own): {}", e.getMessage());
        }
    }

    private OutboxRelayResult relayPending() {
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

    private static boolean isConflict(DaprError e) {
        String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return e.isConflict() || message.contains("etag");
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
