package com.adhar.kit.test.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.ToxiproxyContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ToxiproxyTestContainer}. Only the non-Docker logic (singleton, image, stop of a
 * non-running container) is exercised.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("ToxiproxyTestContainer Tests")
class ToxiproxyTestContainerTest {

    @Test
    @DisplayName("Should create a singleton instance")
    void testGetInstance() {
        ToxiproxyContainer a = ToxiproxyTestContainer.getInstance();
        ToxiproxyContainer b = ToxiproxyTestContainer.getInstance();
        assertNotNull(a);
        assertSame(a, b);
    }

    @Test
    @DisplayName("Should use the toxiproxy image")
    void testDockerImage() {
        assertTrue(ToxiproxyTestContainer.getInstance().getDockerImageName().contains("toxiproxy"));
    }

    @Test
    @DisplayName("Should not fail when stopping a non-running container")
    void testStopNonRunning() {
        assertDoesNotThrow(ToxiproxyTestContainer::stop);
    }
}
