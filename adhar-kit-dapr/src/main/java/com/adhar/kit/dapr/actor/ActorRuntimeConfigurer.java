package com.adhar.kit.dapr.actor;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.config.DaprProperties;
import io.dapr.actors.runtime.ActorRuntime;
import io.dapr.actors.runtime.ActorRuntimeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Sets up the actor runtime when the application starts: one runtime on the kit's sidecar
 * connection, configured from {@code adhar.dapr.actors.*} (idle timeout, scan interval, drain
 * settings), with every {@code @DaprActor} of the application's packages registered through a
 * Spring-aware factory. Without actors nothing is created.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class ActorRuntimeConfigurer implements SmartInitializingSingleton {

    private final DaprConnectionDetails connection;
    private final DaprProperties.ActorsConfig config;
    private final Collection<String> basePackages;
    private final AutowireCapableBeanFactory beanFactory;
    private final ClassLoader classLoader;
    private volatile ActorRuntime runtime;

    public ActorRuntimeConfigurer(DaprConnectionDetails connection, DaprProperties.ActorsConfig config,
                                  Collection<String> basePackages, AutowireCapableBeanFactory beanFactory,
                                  ClassLoader classLoader) {
        this.connection = Objects.requireNonNull(connection, "connection must not be null");
        this.config = config != null ? config : new DaprProperties.ActorsConfig();
        this.basePackages = basePackages == null ? List.of() : List.copyOf(basePackages);
        this.beanFactory = beanFactory;
        this.classLoader = classLoader;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<Class<?>> actors = DaprActorRegistrar.scan(basePackages, classLoader);
        if (actors.isEmpty()) {
            return;
        }
        if (connection.isMemory()) {
            log.warn("{} @DaprActor class(es) found, but Dapr runs in memory mode: actors need a sidecar and are not "
                    + "registered", actors.size());
            return;
        }
        ActorRuntime actorRuntime = runtime();
        DaprActorRegistrar registrar = new DaprActorRegistrar(DaprActorRegistrar.springFactory(() -> actorRuntime,
                beanFactory));
        registrar.registerActors(List.copyOf(actors));
    }

    /**
     * The actor runtime, created on first use with the kit's connection and configuration.
     *
     * @return the runtime
     */
    public ActorRuntime runtime() {
        ActorRuntime current = runtime;
        if (current == null) {
            synchronized (this) {
                if (runtime == null) {
                    runtime = ActorRuntime.getInstance(connection.sdkProperties());
                    apply(runtime.getConfig());
                }
                current = runtime;
            }
        }
        return current;
    }

    private void apply(ActorRuntimeConfig runtimeConfig) {
        if (config.getActorIdleTimeout() != null) {
            runtimeConfig.setActorIdleTimeout(config.getActorIdleTimeout());
        }
        if (config.getActorScanInterval() != null) {
            runtimeConfig.setActorScanInterval(config.getActorScanInterval());
        }
        if (config.getDrainOngoingCallTimeout() != null) {
            runtimeConfig.setDrainOngoingCallTimeout(config.getDrainOngoingCallTimeout());
        }
        runtimeConfig.setDrainBalancedActors(config.isDrainRebalancedActors());
    }
}
