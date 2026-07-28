package com.adhar.kit.test.toxic;

import eu.rekawek.toxiproxy.model.ToxicDirection;
import eu.rekawek.toxiproxy.model.toxic.Latency;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Helpers for injecting network conditions onto a target container's port through a running
 * {@link ToxiproxyContainer}.
 *
 * <p>Typical flow: obtain a {@link ContainerProxy} with {@link #proxy(ToxiproxyContainer,
 * GenericContainer, int)}, point the code under test at {@code proxy.getContainerIpAddress():proxy.getProxyPort()}
 * (from inside the shared Docker network) or at the mapped host port, then apply toxics:</p>
 * <pre>{@code
 * ContainerProxy proxy = NetworkToxics.proxy(toxiproxy, postgres, 5432);
 * NetworkToxics.addLatency(proxy, "slow", 500, 100);   // 500ms +/-100ms jitter downstream
 * NetworkToxics.takeDown(proxy);                        // simulate an outage
 * NetworkToxics.bringUp(proxy);                         // restore
 * }</pre>
 *
 * <p>All toxics are applied on the {@link ToxicDirection#DOWNSTREAM} direction (data flowing from
 * the upstream service back to the client), which is the common case for latency/bandwidth fault
 * injection. Checked {@link IOException}s from the Toxiproxy client are rethrown as
 * {@link UncheckedIOException} to keep test code terse.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public final class NetworkToxics {

    private NetworkToxics() {
    }

    /**
     * Create (or fetch) a proxy in front of {@code targetPort} on {@code target}.
     */
    public static ContainerProxy proxy(ToxiproxyContainer toxiproxy, GenericContainer<?> target, int targetPort) {
        return toxiproxy.getProxy(target, targetPort);
    }

    /**
     * Add a fixed downstream latency toxic, optionally with jitter.
     *
     * @param proxy     the proxy to degrade
     * @param name      unique toxic name (used to remove it later)
     * @param latencyMs added latency in milliseconds ({@code >= 0})
     * @param jitterMs  random jitter in milliseconds ({@code >= 0}; 0 for none)
     */
    public static void addLatency(ContainerProxy proxy, String name, long latencyMs, long jitterMs) {
        require(latencyMs >= 0, "latencyMs must be >= 0");
        require(jitterMs >= 0, "jitterMs must be >= 0");
        try {
            Latency latency = proxy.toxics().latency(name, ToxicDirection.DOWNSTREAM, latencyMs);
            if (jitterMs > 0) {
                latency.setJitter(jitterMs);
            }
            log.debug("Added latency toxic '{}' ({}ms +/-{}ms) to proxy '{}'", name, latencyMs, jitterMs, proxy.getName());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to add latency toxic '" + name + "'", e);
        }
    }

    /**
     * Add a downstream bandwidth cap toxic.
     *
     * @param proxy    the proxy to throttle
     * @param name     unique toxic name
     * @param rateKbps maximum throughput in kilobytes per second ({@code >= 0})
     */
    public static void addBandwidth(ContainerProxy proxy, String name, long rateKbps) {
        require(rateKbps >= 0, "rateKbps must be >= 0");
        try {
            proxy.toxics().bandwidth(name, ToxicDirection.DOWNSTREAM, rateKbps);
            log.debug("Added bandwidth toxic '{}' ({} KB/s) to proxy '{}'", name, rateKbps, proxy.getName());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to add bandwidth toxic '" + name + "'", e);
        }
    }

    /**
     * Remove a previously added toxic by name.
     */
    public static void removeToxic(ContainerProxy proxy, String name) {
        try {
            proxy.toxics().get(name).remove();
            log.debug("Removed toxic '{}' from proxy '{}'", name, proxy.getName());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to remove toxic '" + name + "'", e);
        }
    }

    /**
     * Cut the connection entirely to simulate the backing service being down.
     */
    public static void takeDown(ContainerProxy proxy) {
        proxy.setConnectionCut(true);
        log.debug("Cut connection on proxy '{}'", proxy.getName());
    }

    /**
     * Restore a connection previously cut with {@link #takeDown(ContainerProxy)}.
     */
    public static void bringUp(ContainerProxy proxy) {
        proxy.setConnectionCut(false);
        log.debug("Restored connection on proxy '{}'", proxy.getName());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
