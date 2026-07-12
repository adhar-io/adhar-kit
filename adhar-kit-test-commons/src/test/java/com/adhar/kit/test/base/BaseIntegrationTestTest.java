package com.adhar.kit.test.base;

import com.adhar.kit.test.container.PostgresTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link BaseIntegrationTest}.
 *
 * <p>The static {@code @DynamicPropertySource} and {@code @BeforeAll} hooks are invoked
 * directly. PostgreSQL container construction is intercepted with {@link MockedConstruction}
 * so no Docker container is ever started.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("BaseIntegrationTest Tests")
class BaseIntegrationTestTest {

    /** Captures the suppliers registered against property names. */
    private static class CapturingRegistry implements DynamicPropertyRegistry {
        final Map<String, Supplier<Object>> props = new LinkedHashMap<>();

        @Override
        public void add(String name, Supplier<Object> valueSupplier) {
            props.put(name, valueSupplier);
        }
    }

    private void resetPostgresStatic() throws Exception {
        Field f = PostgresTestContainer.class.getDeclaredField("container");
        f.setAccessible(true);
        f.set(null, null);
    }

    @AfterEach
    void cleanup() throws Exception {
        resetPostgresStatic();
    }

    @Test
    @DisplayName("Should be instantiable as a concrete subclass")
    void testInstantiable() {
        BaseIntegrationTest subject = new BaseIntegrationTest() {
        };
        assertNotNull(subject);
    }

    @Test
    @DisplayName("configureProperties should register datasource and JPA properties")
    void testConfigureProperties() throws Exception {
        resetPostgresStatic();
        CapturingRegistry registry = new CapturingRegistry();

        try (MockedConstruction<PostgreSQLContainer> mocked = mockConstruction(PostgreSQLContainer.class,
                (mock, ctx) -> {
                    when(mock.withDatabaseName(anyString())).thenReturn(mock);
                    when(mock.withUsername(anyString())).thenReturn(mock);
                    when(mock.withPassword(anyString())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost:5432/testdb");
                    when(mock.getUsername()).thenReturn("test");
                    when(mock.getPassword()).thenReturn("test");
                })) {

            BaseIntegrationTest.configureProperties(registry);

            // All six expected properties should have been registered
            assertTrue(registry.props.containsKey("spring.datasource.url"));
            assertTrue(registry.props.containsKey("spring.datasource.username"));
            assertTrue(registry.props.containsKey("spring.datasource.password"));
            assertTrue(registry.props.containsKey("spring.jpa.hibernate.ddl-auto"));
            assertTrue(registry.props.containsKey("spring.jpa.show-sql"));
            assertTrue(registry.props.containsKey("spring.jpa.properties.hibernate.format_sql"));

            // Invoke every supplier so the lambda/method-reference bodies are exercised
            assertEquals("jdbc:postgresql://localhost:5432/testdb",
                    registry.props.get("spring.datasource.url").get());
            assertEquals("test", registry.props.get("spring.datasource.username").get());
            assertEquals("test", registry.props.get("spring.datasource.password").get());
            assertEquals("create-drop", registry.props.get("spring.jpa.hibernate.ddl-auto").get());
            assertEquals("true", registry.props.get("spring.jpa.show-sql").get());
            assertEquals("true", registry.props.get("spring.jpa.properties.hibernate.format_sql").get());
        }
    }

    @Test
    @DisplayName("initializeContainers should start the PostgreSQL container")
    void testInitializeContainers() throws Exception {
        resetPostgresStatic();

        try (MockedConstruction<PostgreSQLContainer> mocked = mockConstruction(PostgreSQLContainer.class,
                (mock, ctx) -> {
                    when(mock.withDatabaseName(anyString())).thenReturn(mock);
                    when(mock.withUsername(anyString())).thenReturn(mock);
                    when(mock.withPassword(anyString())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost:5432/testdb");
                })) {

            BaseIntegrationTest.initializeContainers();

            assertEquals(1, mocked.constructed().size());
            verify(mocked.constructed().get(0)).start();
        }
    }
}
