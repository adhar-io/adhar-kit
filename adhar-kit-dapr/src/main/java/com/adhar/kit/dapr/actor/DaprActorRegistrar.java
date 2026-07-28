package com.adhar.kit.dapr.actor;

import com.adhar.kit.dapr.annotation.DaprActor;
import io.dapr.actors.runtime.AbstractActor;
import io.dapr.actors.runtime.ActorRuntime;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Scans candidate classes for the {@link DaprActor} annotation and registers those that are
 * valid Dapr actors (i.e. also extend {@link AbstractActor}) with the {@link ActorRuntime}.
 *
 * <p>This type only touches the {@code dapr-sdk-actors} optional dependency; it is instantiated
 * exclusively from auto-configuration that is guarded by {@code @ConditionalOnClass}, so a
 * consumer that does not depend on the actors SDK never loads it.</p>
 *
 * <pre>{@code
 * DaprActorRegistrar registrar = new DaprActorRegistrar();
 * registrar.registerActors(List.of(OrderActor.class, ShipmentActor.class));
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class DaprActorRegistrar {

    /**
     * Seam over {@link ActorRuntime#registerActor(Class)} so the registration target can be
     * substituted (e.g. in unit tests) without a running Dapr sidecar.
     */
    @FunctionalInterface
    public interface Registration {
        void register(Class<? extends AbstractActor> actorClass);
    }

    private final Registration registration;

    /**
     * Registers against the singleton {@link ActorRuntime}. The runtime is resolved lazily on
     * first registration so that merely constructing this bean never contacts the sidecar.
     */
    public DaprActorRegistrar() {
        this(actorClass -> ActorRuntime.getInstance().registerActor(actorClass));
    }

    public DaprActorRegistrar(Registration registration) {
        this.registration = Objects.requireNonNull(registration, "registration must not be null");
    }

    /**
     * Registers every candidate that is a valid Dapr actor.
     *
     * @param candidates classes to inspect (non-actors are silently skipped)
     * @return the list of classes that were registered
     */
    public List<Class<? extends AbstractActor>> registerActors(Collection<Class<?>> candidates) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        List<Class<? extends AbstractActor>> registered = new ArrayList<>();
        for (Class<?> candidate : candidates) {
            if (isDaprActor(candidate)) {
                Class<? extends AbstractActor> actorClass = candidate.asSubclass(AbstractActor.class);
                registration.register(actorClass);
                registered.add(actorClass);
                log.info("Registered Dapr actor: type={}, class={}",
                    actorType(candidate), candidate.getName());
            } else if (candidate.isAnnotationPresent(DaprActor.class)) {
                log.warn("@DaprActor class {} does not extend AbstractActor; skipping registration",
                    candidate.getName());
            }
        }
        return registered;
    }

    /**
     * @return {@code true} if {@code clazz} is annotated {@link DaprActor} and extends
     *         {@link AbstractActor}
     */
    public static boolean isDaprActor(Class<?> clazz) {
        return clazz != null
            && clazz.isAnnotationPresent(DaprActor.class)
            && AbstractActor.class.isAssignableFrom(clazz);
    }

    /**
     * Resolves the actor type name for a {@link DaprActor}-annotated class, falling back to the
     * simple class name when {@link DaprActor#type()} is blank.
     */
    public static String actorType(Class<?> clazz) {
        DaprActor annotation = clazz.getAnnotation(DaprActor.class);
        if (annotation != null && annotation.type() != null && !annotation.type().isBlank()) {
            return annotation.type();
        }
        return clazz.getSimpleName();
    }
}
