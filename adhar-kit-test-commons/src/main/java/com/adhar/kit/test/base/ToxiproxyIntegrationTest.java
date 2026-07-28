package com.adhar.kit.test.base;

import com.adhar.kit.test.container.TestContainerRegistry;
import com.adhar.kit.test.container.ToxiproxyTestContainer;
import com.adhar.kit.test.toxic.NetworkToxics;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.ToxiproxyContainer;
import org.testcontainers.containers.ToxiproxyContainer.ContainerProxy;

/**
 * Base class for resilience/chaos integration tests that route a backing container's traffic through
 * Toxiproxy so network faults (latency, bandwidth limits, outages) can be injected.
 *
 * <p>Toxiproxy is started through the shared {@link TestContainerRegistry} and therefore joins the
 * shared Testcontainers network - the target container it proxies must be started through the same
 * registry so they can reach each other. Use {@link #proxy(GenericContainer, int)} to obtain a
 * proxy and {@link NetworkToxics} to apply toxics.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@SpringBootTest
public abstract class ToxiproxyIntegrationTest {

    @BeforeAll
    static void initializeContainers() {
        TestContainerRegistry.getInstance().registerAndStart("toxiproxy", ToxiproxyTestContainer.getInstance());
    }

    /**
     * The running Toxiproxy container.
     */
    protected ToxiproxyContainer toxiproxy() {
        return ToxiproxyTestContainer.getInstance();
    }

    /**
     * Create a proxy in front of {@code targetPort} on {@code target}, through which toxics can be
     * injected with {@link NetworkToxics}.
     */
    protected ContainerProxy proxy(GenericContainer<?> target, int targetPort) {
        return NetworkToxics.proxy(toxiproxy(), target, targetPort);
    }
}
