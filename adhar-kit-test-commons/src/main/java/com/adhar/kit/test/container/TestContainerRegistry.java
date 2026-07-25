package com.adhar.kit.test.container;

import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.lifecycle.Startable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Central coordinator that reconciles the two container mechanisms in this module:
 * the {@link com.adhar.kit.test.TestContainerFacade} instance API and the static
 * {@code *TestContainer} helpers ({@link PostgresTestContainer}, {@link MongoTestContainer},
 * {@link RedisTestContainer}, {@link KafkaTestContainer}) used by the {@code base.*IntegrationTest}
 * classes.
 *
 * <p>Both mechanisms register their running containers here so there is a single source of
 * truth for what is running, in what order it was started, and a shared Testcontainers
 * {@link Network} that containers can join before they start. Neither existing public API
 * changes shape - this class only adds bookkeeping and ordering underneath them.</p>
 *
 * <p><b>Ordering:</b> registrations are tracked in a {@link LinkedHashMap} so iteration order
 * mirrors registration (i.e. startup) order. {@link #stopAll()} tears containers down in the
 * reverse order, so the container started last is stopped first.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public final class TestContainerRegistry {

    private static volatile TestContainerRegistry instance;

    private final Map<String, Startable> containers = new LinkedHashMap<>();
    private Network network;

    private TestContainerRegistry() {
        log.debug("Initialized TestContainerRegistry");
    }

    /**
     * Get the singleton registry instance.
     */
    public static TestContainerRegistry getInstance() {
        if (instance == null) {
            synchronized (TestContainerRegistry.class) {
                if (instance == null) {
                    instance = new TestContainerRegistry();
                }
            }
        }
        return instance;
    }

    /**
     * Get the shared Testcontainers network, creating it lazily on first use.
     * Real Docker network creation is deferred by Testcontainers until a container
     * actually joins the network and starts, so calling this is safe without Docker.
     */
    public synchronized Network network() {
        if (network == null) {
            network = Network.newNetwork();
            log.debug("Created shared Testcontainers network");
        }
        return network;
    }

    /**
     * Register a container under a name without starting it. Safe to call multiple
     * times for the same name; the latest reference wins.
     */
    public synchronized void register(String name, Startable container) {
        containers.put(name, container);
        log.debug("Registered container '{}'", name);
    }

    /**
     * Register a container and start it if it is not already running. If the container is a
     * {@link GenericContainer} that has not started yet, it is attached to the {@link #network()}
     * before starting. Already-running containers are registered but left untouched, since
     * Testcontainers rejects configuration changes (including network attachment) after start.
     */
    public synchronized void registerAndStart(String name, Startable container) {
        boolean alreadyRunning = isRunning(container);
        if (!alreadyRunning && container instanceof GenericContainer<?> genericContainer) {
            genericContainer.withNetwork(network());
        }
        register(name, container);
        if (!alreadyRunning) {
            container.start();
            log.info("Started container '{}'", name);
        } else {
            log.debug("Container '{}' already running, skipping start", name);
        }
    }

    /**
     * Remove a container registration. Does not stop it.
     */
    public synchronized void unregister(String name) {
        containers.remove(name);
    }

    /**
     * Whether a container is currently registered under the given name.
     */
    public synchronized boolean isRegistered(String name) {
        return containers.containsKey(name);
    }

    /**
     * The names of currently registered containers, in registration (startup) order.
     */
    public synchronized List<String> registrationOrder() {
        return List.copyOf(containers.keySet());
    }

    /**
     * How many containers are currently registered.
     */
    public synchronized int size() {
        return containers.size();
    }

    /**
     * Stop every registered container in reverse registration order and clear the registry.
     * Failures stopping one container do not prevent the others from being stopped.
     */
    public synchronized void stopAll() {
        List<String> names = new ArrayList<>(containers.keySet());
        Collections.reverse(names);
        for (String name : names) {
            Startable container = containers.get(name);
            try {
                container.stop();
                log.info("Stopped container '{}'", name);
            } catch (RuntimeException e) {
                log.warn("Failed to stop container '{}'", name, e);
            }
        }
        containers.clear();
    }

    private boolean isRunning(Startable container) {
        return container instanceof GenericContainer<?> genericContainer && genericContainer.isRunning();
    }
}
