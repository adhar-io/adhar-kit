package com.adhar.kit.eventsourcing.snapshot;

import java.time.Instant;

/**
 * Immutable representation of a point-in-time capture of an aggregate's internal state.
 *
 * <p>Snapshots allow an {@code AggregateRepository} to avoid replaying an aggregate's
 * entire event history: instead it restores the aggregate from the latest snapshot and
 * replays only the events recorded after the snapshot's version.</p>
 *
 * @param aggregateId   identifier of the aggregate this snapshot belongs to
 * @param aggregateType fully qualified type name of the aggregate
 * @param version       the aggregate version at which this snapshot was captured
 * @param payload       serialized (e.g. JSON) representation of the aggregate's internal state
 * @param timestamp     when the snapshot was captured
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record Snapshot(
        String aggregateId,
        String aggregateType,
        int version,
        String payload,
        Instant timestamp
) {
}
