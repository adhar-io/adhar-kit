package com.adhar.kit.eventsourcing.projection;

/**
 * Tracks how far each {@link Projection} has progressed through the event stream.
 *
 * <p>The checkpoint is an opaque, monotonically increasing position (the number of events
 * the projection has processed). It allows {@link ProjectionManager#rebuild} to report and
 * resume progress, and lets operators verify a projection is caught up.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface ProjectionCheckpointStore {

    /**
     * Returns the current checkpoint position for a projection.
     *
     * @param projectionName the projection's name
     * @return the last recorded position, or {@code 0} if none has been recorded
     */
    long getCheckpoint(String projectionName);

    /**
     * Records the checkpoint position for a projection.
     *
     * @param projectionName the projection's name
     * @param position       the new checkpoint position
     */
    void saveCheckpoint(String projectionName, long position);
}
