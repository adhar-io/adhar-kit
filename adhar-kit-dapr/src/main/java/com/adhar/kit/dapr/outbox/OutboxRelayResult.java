package com.adhar.kit.dapr.outbox;

import lombok.Value;

/**
 * Summary of a single {@link OutboxPublisher#relay()} pass.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Value
public class OutboxRelayResult {

    /** Events successfully published in this pass. */
    int published;

    /** Events that failed but remain pending for a future retry. */
    int retried;

    /** Events moved to {@link OutboxStatus#DEAD} after exhausting attempts. */
    int dead;

    /** @return total events processed in this pass. */
    public int total() {
        return published + retried + dead;
    }
}
