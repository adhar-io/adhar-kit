package com.adhar.kit.dapr;

import java.time.Duration;
import java.util.Objects;

/**
 * Calls to actors from outside them, through the sidecar's HTTP API. For typed proxies use
 * {@code DaprActorProxyFactory} (needs {@code dapr-sdk-actors}).
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class DaprActorOperations {

    private final DaprFacade dapr;

    DaprActorOperations(DaprFacade dapr) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
    }

    /** Invokes {@code method} on actor {@code actorType/actorId}; data and response are JSON. */
    public <R> R invoke(String actorType, String actorId, String method, Object data, Class<R> responseType) {
        return dapr.invokeActor(actorType, actorId, method, data, responseType);
    }

    public void invoke(String actorType, String actorId, String method, Object data) {
        dapr.invokeActor(actorType, actorId, method, data, Void.class);
    }

    /** Registers a persistent reminder; the actor must implement {@code Remindable}. */
    public void registerReminder(String actorType, String actorId, String name, Duration dueTime, Duration period,
                                 Object data) {
        dapr.registerActorReminder(actorType, actorId, name, dueTime, period, data);
    }
}
