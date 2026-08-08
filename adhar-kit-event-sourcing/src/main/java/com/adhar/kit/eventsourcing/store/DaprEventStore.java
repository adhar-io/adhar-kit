package com.adhar.kit.eventsourcing.store;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateWithETag;
import com.adhar.kit.eventsourcing.bus.DomainEventKafkaSerde;
import com.adhar.kit.eventsourcing.core.DomainEvent;
import com.adhar.kit.eventsourcing.upcast.UpcasterChain;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link EventStore} implementation over the Dapr state building block.
 *
 * <p>Each aggregate's event stream is stored as one JSON document under the
 * key {@code "es:{aggregateId}"} in the configured Dapr state store. Appends
 * use Dapr ETags (first-write concurrency) so two concurrent writers cannot
 * both extend the same stream: the loser gets a {@link ConcurrencyException},
 * mirroring {@link JpaEventStore} semantics (version check against the last
 * stored event's version, then conditional write).</p>
 *
 * <p>Events are stored as serialized envelopes via the transport-neutral
 * {@link DomainEventKafkaSerde} and run through the {@link UpcasterChain} on
 * read, identical to the JPA store.</p>
 *
 * <p><b>Shape caveat:</b> a whole stream is one state entry, which is ideal for
 * aggregates with modest event counts (use snapshotting for long-lived ones).
 * {@link #getAllEvents()} is unsupported - Dapr state stores expose no key
 * enumeration.</p>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class DaprEventStore implements EventStore {

    private static final String KEY_PREFIX = "es:";

    private final DaprFacade daprFacade;
    private final String stateStoreName;
    private final DomainEventKafkaSerde serde;
    private final UpcasterChain upcasterChain;

    /**
     * Creates the Dapr-backed event store.
     *
     * @param daprFacade     the Dapr facade used for state operations
     * @param stateStoreName the Dapr state store component name
     * @param serde          the domain-event envelope serde
     * @param upcasterChain  upcasters applied to events on read
     */
    public DaprEventStore(DaprFacade daprFacade, String stateStoreName,
                          DomainEventKafkaSerde serde, UpcasterChain upcasterChain) {
        this.daprFacade = Objects.requireNonNull(daprFacade, "daprFacade must not be null");
        this.stateStoreName = Objects.requireNonNull(stateStoreName, "stateStoreName must not be null");
        this.serde = Objects.requireNonNull(serde, "serde must not be null");
        this.upcasterChain = Objects.requireNonNull(upcasterChain, "upcasterChain must not be null");
    }

    /**
     * JSON document holding one aggregate's serialized event stream.
     */
    public static class StreamDocument {
        private int version;
        private List<String> events = new ArrayList<>();

        public int getVersion() {
            return version;
        }

        public void setVersion(int version) {
            this.version = version;
        }

        public List<String> getEvents() {
            return events;
        }

        public void setEvents(List<String> events) {
            this.events = events;
        }
    }

    @Override
    public void saveEvents(String aggregateId, List<DomainEvent> events, int expectedVersion) {
        if (events == null || events.isEmpty()) {
            return;
        }
        String key = key(aggregateId);
        StateWithETag<StreamDocument> current =
                daprFacade.getStateWithETag(stateStoreName, key, StreamDocument.class);
        StreamDocument document = current != null && current.getValue() != null
                ? current.getValue() : new StreamDocument();
        String etag = current != null ? current.getEtag() : null;

        if (document.getVersion() != expectedVersion) {
            throw new ConcurrencyException(aggregateId, expectedVersion, document.getVersion());
        }

        for (DomainEvent event : events) {
            document.getEvents().add(serde.serialize(event));
            document.setVersion(event.version());
        }

        boolean saved = daprFacade.saveStateWithETag(stateStoreName, key, document, etag);
        if (!saved) {
            // Someone else extended the stream between our read and write.
            StateWithETag<StreamDocument> latest =
                    daprFacade.getStateWithETag(stateStoreName, key, StreamDocument.class);
            int actual = latest != null && latest.getValue() != null
                    ? latest.getValue().getVersion() : 0;
            throw new ConcurrencyException(aggregateId, expectedVersion, actual);
        }
        log.debug("Appended {} event(s) to Dapr stream '{}' (version {})",
                events.size(), key, document.getVersion());
    }

    @Override
    public List<DomainEvent> getEvents(String aggregateId) {
        StreamDocument document = daprFacade.getState(stateStoreName, key(aggregateId), StreamDocument.class);
        if (document == null || document.getEvents().isEmpty()) {
            return List.of();
        }
        return upcasterChain.applyAll(document.getEvents().stream()
                .map(serde::deserialize)
                .toList());
    }

    @Override
    public List<DomainEvent> getEventsAfterVersion(String aggregateId, int version) {
        return getEvents(aggregateId).stream()
                .filter(event -> event.version() > version)
                .toList();
    }

    /**
     * The Dapr state store component name this store writes to.
     */
    public String getStateStoreName() {
        return stateStoreName;
    }

    private String key(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return KEY_PREFIX + aggregateId;
    }
}
