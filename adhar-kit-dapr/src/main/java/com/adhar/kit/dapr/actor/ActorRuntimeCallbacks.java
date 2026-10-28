package com.adhar.kit.dapr.actor;

import io.dapr.actors.runtime.ActorRuntime;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * {@link DaprActorCallbacks} on the SDK's {@link ActorRuntime}.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class ActorRuntimeCallbacks implements DaprActorCallbacks {

    private final Supplier<ActorRuntime> runtime;

    public ActorRuntimeCallbacks(Supplier<ActorRuntime> runtime) {
        this.runtime = Objects.requireNonNull(runtime, "runtime must not be null");
    }

    @Override
    public byte[] config() {
        try {
            return runtime.get().serializeConfig();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not serialize the actor configuration", e);
        }
    }

    @Override
    public byte[] invoke(String actorType, String actorId, String method, byte[] body) {
        return runtime.get().invoke(actorType, actorId, method, body).block();
    }

    @Override
    public void timer(String actorType, String actorId, String timer, byte[] body) {
        runtime.get().invokeTimer(actorType, actorId, timer, body).block();
    }

    @Override
    public void reminder(String actorType, String actorId, String reminder, byte[] body) {
        runtime.get().invokeReminder(actorType, actorId, reminder, body).block();
    }

    @Override
    public void deactivate(String actorType, String actorId) {
        runtime.get().deactivate(actorType, actorId).block();
    }
}
