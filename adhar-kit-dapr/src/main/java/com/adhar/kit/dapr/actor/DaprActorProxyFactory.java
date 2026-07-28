package com.adhar.kit.dapr.actor;

import io.dapr.actors.ActorId;
import io.dapr.actors.client.ActorClient;
import io.dapr.actors.client.ActorProxyBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

/**
 * Typed factory for Dapr actor proxies. Wraps {@link ActorProxyBuilder} / {@link ActorClient}
 * so callers obtain a strongly-typed client-side proxy for a remote actor by interface, type,
 * and id.
 *
 * <p>Only referenced from actors-gated auto-configuration, so the optional
 * {@code dapr-sdk-actors} dependency is never required by consumers that don't use actors.</p>
 *
 * <pre>{@code
 * DaprActorProxyFactory factory = new DaprActorProxyFactory();
 * OrderActor actor = factory.create(OrderActor.class, "OrderActor", orderId);
 * actor.process(request);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprActorProxyFactory implements AutoCloseable {

    private final ActorClient actorClient;

    /**
     * Creates a factory backed by a default {@link ActorClient} (connects to the local sidecar).
     */
    public DaprActorProxyFactory() {
        this(new ActorClient());
    }

    public DaprActorProxyFactory(ActorClient actorClient) {
        this.actorClient = Objects.requireNonNull(actorClient, "actorClient must not be null");
    }

    /**
     * Builds a typed proxy for the given actor interface, type and id.
     *
     * @param actorInterface the actor interface (or class) exposing the remote methods
     * @param actorType      the registered actor type name
     * @param actorId        the actor instance id
     * @param <T>            the actor interface type
     * @return a client-side proxy
     */
    public <T> T create(Class<T> actorInterface, String actorType, String actorId) {
        Objects.requireNonNull(actorInterface, "actorInterface must not be null");
        if (actorType == null || actorType.isBlank()) {
            throw new IllegalArgumentException("actorType must not be blank");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId must not be blank");
        }
        log.debug("Creating actor proxy: type={}, id={}, interface={}",
            actorType, actorId, actorInterface.getName());
        return newBuilder(actorInterface, actorType).build(new ActorId(actorId));
    }

    /**
     * Builds a typed proxy, deriving the actor type from the interface's simple name.
     */
    public <T> T create(Class<T> actorInterface, String actorId) {
        Objects.requireNonNull(actorInterface, "actorInterface must not be null");
        return create(actorInterface, actorInterface.getSimpleName(), actorId);
    }

    /**
     * Seam constructing the underlying {@link ActorProxyBuilder}; overridable for testing.
     */
    protected <T> ActorProxyBuilder<T> newBuilder(Class<T> actorInterface, String actorType) {
        return new ActorProxyBuilder<>(actorType, actorInterface, actorClient);
    }

    @Override
    public void close() {
        try {
            actorClient.close();
        } catch (Exception e) {
            log.error("Failed to close actor client", e);
        }
    }
}
