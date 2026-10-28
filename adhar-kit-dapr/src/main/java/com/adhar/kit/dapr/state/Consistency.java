package com.adhar.kit.dapr.state;

/**
 * State read/write consistency, as Dapr defines it. {@link #EVENTUAL} is Dapr's default.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public enum Consistency {

    /** The store may answer before every replica has the write. */
    EVENTUAL,

    /** The store answers only once a quorum has the write. */
    STRONG;

    io.dapr.client.domain.StateOptions.Consistency toSdk() {
        return this == STRONG
                ? io.dapr.client.domain.StateOptions.Consistency.STRONG
                : io.dapr.client.domain.StateOptions.Consistency.EVENTUAL;
    }
}
