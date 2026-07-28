package com.adhar.kit.test.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
// getHost()/mapped-port accessors require a running Docker environment and are covered only
// by the Docker-gated integration path, not here.

/**
 * Tests for {@link DaprTestContainer}. Only the non-Docker logic (singleton, image, exposed ports,
 * stop of a non-running container) is exercised.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("DaprTestContainer Tests")
class DaprTestContainerTest {

    @Test
    @DisplayName("Should create a singleton instance")
    void testGetInstance() {
        GenericContainer<?> a = DaprTestContainer.getInstance();
        GenericContainer<?> b = DaprTestContainer.getInstance();
        assertNotNull(a);
        assertSame(a, b);
    }

    @Test
    @DisplayName("Should use the daprd image and expose the HTTP and gRPC ports")
    void testImageAndPorts() {
        GenericContainer<?> container = DaprTestContainer.getInstance();
        assertTrue(container.getDockerImageName().contains("daprd"));
        assertTrue(container.getExposedPorts().contains(3500));
        assertTrue(container.getExposedPorts().contains(50001));
    }

    @Test
    @DisplayName("Should not fail when stopping a non-running container")
    void testStopNonRunning() {
        assertDoesNotThrow(DaprTestContainer::stop);
    }
}
