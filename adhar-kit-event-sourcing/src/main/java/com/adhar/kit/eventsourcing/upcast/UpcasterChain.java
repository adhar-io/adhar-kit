package com.adhar.kit.eventsourcing.upcast;

import com.adhar.kit.eventsourcing.core.DomainEvent;

import java.util.List;

/**
 * Applies a sequence of {@link EventUpcaster}s to migrate a stored event to its current
 * schema before it is handed back to callers (aggregate replay, projections, etc.).
 *
 * <p>Upcasters are tried in registration order on every pass. Because a single upcast may
 * produce an event that a later upcaster in the chain also applies to (e.g. v1&rarr;v2 then
 * v2&rarr;v3), the chain repeats passes until none of the upcasters make further changes, up
 * to a safety bound to prevent misconfigured (cyclic) chains from looping forever.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class UpcasterChain {

    private final List<EventUpcaster> upcasters;

    public UpcasterChain(List<EventUpcaster> upcasters) {
        this.upcasters = List.copyOf(upcasters);
    }

    /**
     * Returns an empty chain that leaves every event unchanged.
     *
     * @return a no-op upcaster chain
     */
    public static UpcasterChain empty() {
        return new UpcasterChain(List.of());
    }

    /**
     * Applies all applicable upcasters, in order, to the given event.
     *
     * @param event the event to migrate
     * @return the migrated event (or the original event if no upcaster applied)
     */
    public DomainEvent apply(DomainEvent event) {
        DomainEvent current = event;
        int maxPasses = upcasters.size() + 1;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = false;
            for (EventUpcaster upcaster : upcasters) {
                if (upcaster.supports(current.eventType(), current.version())) {
                    current = upcaster.upcast(current);
                    changed = true;
                }
            }
            if (!changed) {
                break;
            }
        }
        return current;
    }

    /**
     * Applies {@link #apply(DomainEvent)} to every event in the given list.
     *
     * @param events the events to migrate
     * @return the migrated events, in the same order
     */
    public List<DomainEvent> applyAll(List<DomainEvent> events) {
        return events.stream().map(this::apply).toList();
    }
}
