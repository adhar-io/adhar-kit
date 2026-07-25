package com.adhar.kit.test.base;

import com.adhar.kit.test.container.KafkaTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.kafka.ConfluentKafkaContainer;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link KafkaIntegrationTest}.
 *
 * <p>Kafka container construction is intercepted with {@link MockedConstruction} so no
 * Docker container is ever started.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@DisplayName("KafkaIntegrationTest Tests")
class KafkaIntegrationTestTest {

    private static class CapturingRegistry implements DynamicPropertyRegistry {
        final Map<String, Supplier<Object>> props = new LinkedHashMap<>();

        @Override
        public void add(String name, Supplier<Object> valueSupplier) {
            props.put(name, valueSupplier);
        }
    }

    private void resetStatics() throws Exception {
        Field containerField = KafkaTestContainer.class.getDeclaredField("container");
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
        KafkaIntegrationTest subject = new KafkaIntegrationTest() {
        };
        assertNotNull(subject);
    }

    @Test
    @DisplayName("initializeContainers should start Kafka and register it with the shared registry")
    void testInitializeContainers() throws Exception {
        try (MockedConstruction<ConfluentKafkaContainer> mocked = mockConstruction(ConfluentKafkaContainer.class,
                (mock, ctx) -> {
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getBootstrapServers()).thenReturn("PLAINTEXT://localhost:9092");
                    when(mock.isRunning()).thenReturn(false);
                })) {

            KafkaIntegrationTest.initializeContainers();

            assertEquals(1, mocked.constructed().size());
            ConfluentKafkaContainer created = mocked.constructed().get(0);
            verify(created).start();
            assertTrue(TestContainerRegistry.getInstance().isRegistered("kafka"));
        }
    }

    @Test
    @DisplayName("configureProperties should register the Kafka bootstrap-servers property")
    void testConfigureProperties() throws Exception {
        try (MockedConstruction<ConfluentKafkaContainer> mocked = mockConstruction(ConfluentKafkaContainer.class,
                (mock, ctx) -> {
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getBootstrapServers()).thenReturn("PLAINTEXT://localhost:9092");
                })) {

            CapturingRegistry registry = new CapturingRegistry();
            KafkaIntegrationTest.configureProperties(registry);

            assertTrue(registry.props.containsKey("spring.kafka.bootstrap-servers"));
            assertEquals("PLAINTEXT://localhost:9092",
                    registry.props.get("spring.kafka.bootstrap-servers").get());
        }
    }
}
