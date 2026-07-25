package com.adhar.kit.test.base;

import com.adhar.kit.test.container.RedisTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link RedisIntegrationTest}.
 *
 * <p>Redis container construction is intercepted with {@link MockedConstruction} so no
 * Docker container is ever started.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@DisplayName("RedisIntegrationTest Tests")
class RedisIntegrationTestTest {

    private static class CapturingRegistry implements DynamicPropertyRegistry {
        final Map<String, Supplier<Object>> props = new LinkedHashMap<>();

        @Override
        public void add(String name, Supplier<Object> valueSupplier) {
            props.put(name, valueSupplier);
        }
    }

    private void resetStatics() throws Exception {
        Field containerField = RedisTestContainer.class.getDeclaredField("container");
        containerField.setAccessible(true);
        containerField.set(null, null);

        Field registryField = TestContainerRegistry.class.getDeclaredField("instance");
        registryField.setAccessible(true);
        registryField.set(null, null);
    }

    @BeforeEach
    void setUp() throws Exception {
        resetStatics();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetStatics();
    }

    @Test
    @DisplayName("Should be instantiable as a concrete subclass")
    void testInstantiable() {
        RedisIntegrationTest subject = new RedisIntegrationTest() {
        };
        assertNotNull(subject);
    }

    @Test
    @DisplayName("initializeContainers should start Redis and register it with the shared registry")
    void testInitializeContainers() throws Exception {
        try (MockedConstruction<GenericContainer> mocked = mockConstruction(GenericContainer.class,
                (mock, ctx) -> {
                    when(mock.withExposedPorts(anyInt())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getHost()).thenReturn("localhost");
                    when(mock.getMappedPort(6379)).thenReturn(6379);
                    when(mock.isRunning()).thenReturn(false);
                })) {

            RedisIntegrationTest.initializeContainers();

            assertEquals(1, mocked.constructed().size());
            GenericContainer<?> created = mocked.constructed().get(0);
            verify(created).start();
            assertTrue(TestContainerRegistry.getInstance().isRegistered("redis"));
        }
    }

    @Test
    @DisplayName("configureProperties should register Redis host and port properties")
    void testConfigureProperties() throws Exception {
        try (MockedConstruction<GenericContainer> mocked = mockConstruction(GenericContainer.class,
                (mock, ctx) -> {
                    when(mock.withExposedPorts(anyInt())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getHost()).thenReturn("localhost");
                    when(mock.getMappedPort(6379)).thenReturn(6380);
                })) {

            CapturingRegistry registry = new CapturingRegistry();
            RedisIntegrationTest.configureProperties(registry);

            assertTrue(registry.props.containsKey("spring.data.redis.host"));
            assertTrue(registry.props.containsKey("spring.data.redis.port"));
            assertEquals("localhost", registry.props.get("spring.data.redis.host").get());
            assertEquals(6380, registry.props.get("spring.data.redis.port").get());
        }
    }
}
