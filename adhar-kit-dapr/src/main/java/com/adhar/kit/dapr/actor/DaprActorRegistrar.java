package com.adhar.kit.dapr.actor;

import com.adhar.kit.dapr.annotation.DaprActor;
import io.dapr.actors.ActorId;
import io.dapr.actors.ActorUtils;
import io.dapr.actors.runtime.AbstractActor;
import io.dapr.actors.runtime.ActorFactory;
import io.dapr.actors.runtime.ActorRuntime;
import io.dapr.actors.runtime.ActorRuntimeContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Scans candidate classes for the {@link DaprActor} annotation and registers those that are
 * valid Dapr actors (i.e. also extend {@link AbstractActor}) with the {@link ActorRuntime}.
 *
 * <p>This type only touches the {@code dapr-sdk-actors} optional dependency; it is instantiated
 * exclusively from auto-configuration that is guarded by {@code @ConditionalOnClass}, so a
 * consumer that does not depend on the actors SDK never loads it. In a Spring application the
 * auto-configuration scans the application's packages ({@link #scan}) and registers every actor
 * with a {@link #springFactory Spring-aware factory}, so actors can {@code @Autowired} beans.</p>
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

    /**
     * Registers against {@code runtime}, creating each actor through Spring: the actor's
     * {@code (ActorRuntimeContext, ActorId)} constructor runs, then its fields are autowired.
     *
     * @param runtime     the actor runtime (resolved on first registration)
     * @param beanFactory the bean factory that autowires actors
     * @return the registration
     */
    public static Registration springFactory(Supplier<ActorRuntime> runtime, AutowireCapableBeanFactory beanFactory) {
        return actorClass -> register(runtime.get(), actorClass, beanFactory);
    }

    private static <T extends AbstractActor> void register(ActorRuntime runtime, Class<T> actorClass,
                                                           AutowireCapableBeanFactory beanFactory) {
        Constructor<T> constructor = actorConstructor(actorClass);
        ActorFactory<T> factory = (ActorRuntimeContext<T> context, ActorId id) -> {
            T actor = BeanUtils.instantiateClass(constructor, context, id);
            beanFactory.autowireBean(actor);
            return actor;
        };
        runtime.registerActor(actorClass, factory);
    }

    private static <T> Constructor<T> actorConstructor(Class<T> actorClass) {
        for (Constructor<?> candidate : actorClass.getDeclaredConstructors()) {
            Class<?>[] types = candidate.getParameterTypes();
            if (types.length == 2 && types[0] == ActorRuntimeContext.class && types[1] == ActorId.class) {
                @SuppressWarnings("unchecked")
                Constructor<T> constructor = (Constructor<T>) candidate;
                return constructor;
            }
        }
        throw new IllegalStateException("Actor " + actorClass.getName()
                + " needs a constructor (ActorRuntimeContext<...>, ActorId)");
    }

    /**
     * Finds the {@link DaprActor} classes below {@code basePackages}.
     *
     * @param basePackages packages to scan
     * @param classLoader  the class loader
     * @return the annotated classes
     */
    public static Set<Class<?>> scan(Collection<String> basePackages, ClassLoader classLoader) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(
                    org.springframework.beans.factory.annotation.AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isIndependent() && !definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(DaprActor.class));
        Set<Class<?>> classes = new LinkedHashSet<>();
        for (String basePackage : basePackages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(basePackage)) {
                try {
                    classes.add(ClassUtils.forName(candidate.getBeanClassName(), classLoader));
                } catch (ClassNotFoundException | LinkageError e) {
                    log.warn("Could not load @DaprActor class {}: {}", candidate.getBeanClassName(), e.getMessage());
                }
            }
        }
        return classes;
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
                checkTypeName(candidate);
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
     * The runtime registers an actor under the name the SDK derives ({@code @ActorType} on the
     * class or its interface, else the simple class name), not under {@link DaprActor#type()};
     * a mismatch would leave callers addressing a type nobody hosts.
     */
    @SuppressWarnings("deprecation")
    private static void checkTypeName(Class<?> candidate) {
        DaprActor annotation = candidate.getAnnotation(DaprActor.class);
        String sdkName = ActorUtils.findActorTypeName(candidate);
        if (annotation.type() != null && !annotation.type().isBlank() && !annotation.type().equals(sdkName)) {
            log.error("@DaprActor(type = \"{}\") on {} is registered as \"{}\": the SDK names actor types from "
                    + "@ActorType; add @ActorType(name = \"{}\") to the actor interface", annotation.type(),
                    candidate.getName(), sdkName, annotation.type());
        }
        if (!"60m".equals(annotation.idleTimeout())) {
            log.warn("@DaprActor(idleTimeout = \"{}\") on {} is ignored: the Java SDK has one idle timeout for every "
                    + "actor type (adhar.dapr.actors.actor-idle-timeout)", annotation.idleTimeout(), candidate.getName());
        }
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
