package com.adhar.kit.eventsourcing.projection;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * In-memory {@link ProjectionCheckpointStore} implementation intended for development and testing.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class InMemoryProjectionCheckpointStore implements ProjectionCheckpointStore {

    private final ConcurrentMap<String, Long> checkpoints = new ConcurrentHashMap<>();

    @Override
    public long getCheckpoint(String projectionName) {
        return checkpoints.getOrDefault(projectionName, 0L);
    }

    @Override
    public void saveCheckpoint(String projectionName, long position) {
        checkpoints.put(projectionName, position);
    }
}
