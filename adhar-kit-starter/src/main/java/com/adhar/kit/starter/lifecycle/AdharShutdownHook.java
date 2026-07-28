package com.adhar.kit.starter.lifecycle;

/**
 * Per-module hook into Adhar Kit's coordinated graceful shutdown.
 *
 * <p>When the application context closes, {@link AdharGracefulShutdown} drives
 * every registered hook through three ordered phases so modules wind down
 * cleanly rather than being torn down abruptly:</p>
 * <ol>
 *   <li>{@link #stopAcceptingWork()} - stop taking on new work (close listeners,
 *       reject new requests, pause consumers);</li>
 *   <li>{@link #drain()} - let in-flight work finish (await queues to empty,
 *       flush buffers);</li>
 *   <li>{@link #close()} - release resources (close facades, pools, connections).</li>
 * </ol>
 *
 * <p>Each phase runs for <em>all</em> hooks (in {@link #order()} order) before the
 * next phase begins, so, for example, every module stops accepting work before
 * any module starts draining.</p>
 *
 * <p>Hooks are discovered two ways and merged:</p>
 * <ul>
 *   <li><b>Spring beans</b> - any bean implementing this interface is picked up
 *       automatically by the starter auto-configuration.</li>
 *   <li><b>Via {@link java.util.ServiceLoader}</b> - declared under
 *       {@code META-INF/services/com.adhar.kit.starter.lifecycle.AdharShutdownHook},
 *       for modules that are not Spring-managed.</li>
 * </ul>
 *
 * <p>All methods default to no-ops, so an implementation need only override the
 * phases it cares about.</p>
 *
 * @author Tapas Jena
 * @since 0.1.0
 */
public interface AdharShutdownHook {

    /**
     * A human-readable module name for shutdown logging. Defaults to the simple
     * class name.
     *
     * @return the module name
     */
    default String moduleName() {
        return getClass().getSimpleName();
    }

    /**
     * Relative shutdown order; lower values are processed earlier within each
     * phase. Defaults to {@code 0}.
     *
     * @return the shutdown order
     */
    default int order() {
        return 0;
    }

    /**
     * Phase 1: stop accepting new work. Should return promptly.
     */
    default void stopAcceptingWork() {
    }

    /**
     * Phase 2: drain in-flight work, waiting for it to complete.
     */
    default void drain() {
    }

    /**
     * Phase 3: close facades and release resources.
     */
    default void close() {
    }
}
