package com.adhar.kit.health.lifecycle;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Programmatic readiness gate for graceful shutdown support.
 *
 * <p>Registered as a readiness-group indicator, this manager lets the application
 * (or the platform lifecycle) flip readiness on and off. Marking the instance
 * not-ready makes readiness probes fail so load balancers drain traffic before
 * the process exits.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * ReadinessStateManager readiness = new ReadinessStateManager();
 * registry.register(readiness, HealthRegistry.READINESS_GROUP);
 * readiness.registerShutdownHook();   // flip to not-ready on JVM shutdown
 *
 * // once the app finished starting up:
 * readiness.markReady();
 *
 * // maintenance mode:
 * readiness.markNotReady("Maintenance window");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class ReadinessStateManager implements AdharHealthIndicator {

    /** Indicator name used in health responses. */
    public static final String NAME = "readinessGate";

    private static final String NOT_STARTED_REASON = "Application not yet marked ready";

    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean();
    private volatile String reason;

    /**
     * Creates a manager that starts not-ready.
     */
    public ReadinessStateManager() {
        this(false);
    }

    /**
     * Creates a manager.
     *
     * @param initiallyReady whether the gate starts open (ready)
     */
    public ReadinessStateManager(boolean initiallyReady) {
        this.ready.set(initiallyReady);
        this.reason = initiallyReady ? null : NOT_STARTED_REASON;
    }

    /**
     * Marks the application ready to receive traffic.
     */
    public void markReady() {
        if (!ready.getAndSet(true)) {
            log.info("Readiness gate opened: application is ready to receive traffic");
        }
        reason = null;
    }

    /**
     * Marks the application not-ready so readiness probes fail and traffic drains.
     *
     * @param reason human-readable reason reported in the health response
     */
    public void markNotReady(String reason) {
        this.reason = reason == null ? "Not ready" : reason;
        if (ready.getAndSet(false)) {
            log.warn("Readiness gate closed: {}", this.reason);
        }
    }

    /**
     * Checks whether the gate is open.
     *
     * @return true when ready
     */
    public boolean isReady() {
        return ready.get();
    }

    /**
     * Gets the not-ready reason.
     *
     * @return reason, or null when ready
     */
    public String getReason() {
        return reason;
    }

    /**
     * Registers a JVM shutdown hook that flips the gate to not-ready so load
     * balancers drain traffic before the process exits (graceful shutdown).
     *
     * <p>Idempotent — only the first call registers the hook.</p>
     *
     * @return true when the hook was registered by this call
     */
    public boolean registerShutdownHook() {
        if (shutdownHookRegistered.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::onShutdown, "adhar-readiness-shutdown"));
            log.debug("Registered readiness shutdown hook");
            return true;
        }
        return false;
    }

    /**
     * Flips the gate to not-ready for shutdown. Invoked by the shutdown hook and
     * the Spring context-closed listener.
     */
    public void onShutdown() {
        markNotReady("Application is shutting down");
    }

    @Override
    public Health check() {
        if (ready.get()) {
            return Health.up()
                    .component(NAME)
                    .withDetail("ready", true)
                    .build();
        }
        return Health.outOfService()
                .component(NAME)
                .withDetail("ready", false)
                .withDetail("reason", reason)
                .build();
    }

    @Override
    public String getName() {
        return NAME;
    }
}
