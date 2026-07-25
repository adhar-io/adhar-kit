package com.adhar.kit.test.base;

import com.adhar.kit.test.container.MongoTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MongoDBContainer;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link MongoIntegrationTest}.
 *
 * <p>MongoDB container construction is intercepted with {@link MockedConstruction} so no
 * Docker container is ever started. The static hooks are invoked directly, matching the
 * pattern used by {@code BaseIntegrationTestTest}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@DisplayName("MongoIntegrationTest Tests")
class MongoIntegrationTestTest {

    private static class CapturingRegistry implements DynamicPropertyRegistry {
        final Map<String, Supplier<Object>> props = new LinkedHashMap<>();

        @Override
        public void add(String name, Supplier<Object> valueSupplier) {
            props.put(name, valueSupplier);
        }
    }

    private void resetStatics() throws Exception {
        Field containerField = MongoTestContainer.class.getDeclaredField("container");
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
        MongoIntegrationTest subject = new MongoIntegrationTest() {
        };
        assertNotNull(subject);
    }

    @Test
    @DisplayName("initializeContainers should start Mongo and register it with the shared registry")
    void testInitializeContainers() throws Exception {
        try (MockedConstruction<MongoDBContainer> mocked = mockConstruction(MongoDBContainer.class,
                (mock, ctx) -> {
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getReplicaSetUrl()).thenReturn("mongodb://localhost:27017/test");
                    when(mock.isRunning()).thenReturn(false);
                })) {

            MongoIntegrationTest.initializeContainers();

            assertEquals(1, mocked.constructed().size());
            MongoDBContainer created = mocked.constructed().get(0);
            verify(created).start();
            assertTrue(TestContainerRegistry.getInstance().isRegistered("mongo"));
        }
    }

    @Test
    @DisplayName("configureProperties should register the MongoDB URI property")
    void testConfigureProperties() throws Exception {
        try (MockedConstruction<MongoDBContainer> mocked = mockConstruction(MongoDBContainer.class,
                (mock, ctx) -> {
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getReplicaSetUrl()).thenReturn("mongodb://localhost:27017/test");
                })) {

            CapturingRegistry registry = new CapturingRegistry();
            MongoIntegrationTest.configureProperties(registry);

            assertTrue(registry.props.containsKey("spring.data.mongodb.uri"));
            assertEquals("mongodb://localhost:27017/test",
                    registry.props.get("spring.data.mongodb.uri").get());
        }
    }
}
