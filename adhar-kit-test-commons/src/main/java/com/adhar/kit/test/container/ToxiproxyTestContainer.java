package com.adhar.kit.test.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainer configuration for Toxiproxy - a TCP proxy for deterministically injecting network
 * conditions (latency, limited bandwidth, connection cuts) between the code under test and a real
 * backing container.
 *
 * <p>Follows the same singleton style as the other {@code *TestContainer} helpers. Wrap a target
 * container's port with {@link com.adhar.kit.test.toxic.NetworkToxics#proxy} and then apply toxics
 * through the returned {@code ContainerProxy}. Toxiproxy and the target container must share a
 * network - start the target through {@link TestContainerRegistry} so both join the shared network.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class ToxiproxyTestContainer {

    private static final Logger log = LoggerFactory.getLogger(ToxiproxyTestContainer.class);
    private static final String TOXIPROXY_IMAGE = "ghcr.io/shopify/toxiproxy:2.5.0";

    private static ToxiproxyContainer container;

    /**
     * Get a singleton Toxiproxy container instance.
     */
    public static ToxiproxyContainer getInstance() {
        if (container == null) {
            container = new ToxiproxyContainer(DockerImageName.parse(TOXIPROXY_IMAGE))
                    .withReuse(true);
        }
        return container;
    }

    /**
     * Start the Toxiproxy container.
     */
    public static void start() {
        getInstance().start();
        log.info("Toxiproxy container started, control port: {}", container.getControlPort());
    }

    /**
     * Stop the Toxiproxy container.
     */
    public static void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("Toxiproxy container stopped");
        }
    }

    /**
     * Get the Toxiproxy control (API) port.
     */
    public static int getControlPort() {
        return getInstance().getControlPort();
    }
}
