package com.adhar.kit.dapr.state;

import io.dapr.client.domain.StateOptions;

/**
 * Converts the kit's option values to the SDK's {@link StateOptions}; {@code null} when neither is
 * set, so the call carries Dapr's defaults rather than an explicit choice.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class StateOptionsSupport {

    private StateOptionsSupport() {
    }

    public static StateOptions toSdk(Consistency consistency, Concurrency concurrency) {
        if (consistency == null && concurrency == null) {
            return null;
        }
        return new StateOptions(
                consistency == null ? null : consistency.toSdk(),
                concurrency == null ? null : concurrency.toSdk());
    }
}
