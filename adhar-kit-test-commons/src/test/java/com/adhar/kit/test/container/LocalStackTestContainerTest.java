package com.adhar.kit.test.container;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.localstack.LocalStackContainer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link LocalStackTestContainer}. Only the non-Docker logic (singleton, image, service
 * configuration, stop of a non-running container) is exercised.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("LocalStackTestContainer Tests")
class LocalStackTestContainerTest {

    @Test
    @DisplayName("Should create a singleton instance")
    void testGetInstance() {
        LocalStackContainer a = LocalStackTestContainer.getInstance();
        LocalStackContainer b = LocalStackTestContainer.getInstance();
        assertNotNull(a);
        assertSame(a, b);
    }

    @Test
    @DisplayName("Should use the localstack image")
    void testDockerImage() {
        String image = LocalStackTestContainer.getInstance().getDockerImageName();
        assertTrue(image.contains("localstack"));
    }

    @Test
    @DisplayName("withServices should return the same container for chaining")
    void testWithServices() {
        LocalStackContainer configured = LocalStackTestContainer.withServices("s3", "sqs");
        assertSame(LocalStackTestContainer.getInstance(), configured);
    }

    @Test
    @DisplayName("Should not fail when stopping a non-running container")
    void testStopNonRunning() {
        assertDoesNotThrow(LocalStackTestContainer::stop);
    }
}
