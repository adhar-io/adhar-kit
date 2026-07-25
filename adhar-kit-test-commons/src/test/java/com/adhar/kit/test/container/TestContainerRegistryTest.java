package com.adhar.kit.test.container;

import com.adhar.kit.test.TestContainerFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link TestContainerRegistry}.
 *
 * <p>Bookkeeping (register/unregister/order/size) is exercised with plain Mockito mocks of
 * {@link Startable}/{@link GenericContainer} - no Docker container is ever started. The
 * shared {@link Network} is only ever lazily constructed via {@link Network#newNetwork()},
 * which Testcontainers defers until a container actually joins it and starts, so exercising
 * {@link TestContainerRegistry#network()} here does not require Docker either.</p>
 *
 * <p>The singleton is reset via reflection before and after every test so tests do not leak
 * state into each other or into other test classes that use the registry (e.g.
 * {@link TestContainerFacade}).</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@DisplayName("TestContainerRegistry Tests")
class TestContainerRegistryTest {

    private void resetSingleton() throws Exception {
        Field f = TestContainerRegistry.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    @BeforeEach
    void setUp() throws Exception {
        resetSingleton();
    }

    @AfterEach
    void tearDown() throws Exception {
        resetSingleton();
    }

    @Test
    @DisplayName("getInstance should return a singleton")
    void testSingleton() {
        TestContainerRegistry a = TestContainerRegistry.getInstance();
        TestContainerRegistry b = TestContainerRegistry.getInstance();
        assertNotNull(a);
        assertSame(a, b);
    }

    @Test
    @DisplayName("register should make a container discoverable and track insertion order")
    void testRegisterAndOrder() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        Startable first = mock(Startable.class);
        Startable second = mock(Startable.class);

        registry.register("first", first);
        registry.register("second", second);

        assertTrue(registry.isRegistered("first"));
        assertTrue(registry.isRegistered("second"));
        assertFalse(registry.isRegistered("nope"));
        assertEquals(2, registry.size());
        assertEquals(List.of("first", "second"), registry.registrationOrder());
    }

    @Test
    @DisplayName("register should overwrite an existing entry for the same name")
    void testRegisterOverwrite() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        registry.register("dup", mock(Startable.class));
        registry.register("dup", mock(Startable.class));

        assertEquals(1, registry.size());
        assertEquals(List.of("dup"), registry.registrationOrder());
    }

    @Test
    @DisplayName("unregister should remove a container without stopping it")
    void testUnregister() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        Startable container = mock(Startable.class);
        registry.register("x", container);

        registry.unregister("x");

        assertFalse(registry.isRegistered("x"));
        verifyNoInteractions(container);
    }

    @Test
    @DisplayName("unregister should be a no-op for an unknown name")
    void testUnregisterUnknown() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        assertDoesNotThrow(() -> registry.unregister("nope"));
    }

    @Test
    @DisplayName("network should be a lazily-created singleton")
    void testNetworkSingleton() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        Network a = registry.network();
        Network b = registry.network();
        assertNotNull(a);
        assertSame(a, b);
    }

    @Test
    @DisplayName("registerAndStart should attach the network and start a container that is not running")
    void testRegisterAndStartWhenNotRunning() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();

        @SuppressWarnings("unchecked")
        GenericContainer<?> container = mock(GenericContainer.class);
        when(container.isRunning()).thenReturn(false);
        doReturn(container).when(container).withNetwork(any());

        registry.registerAndStart("fresh", container);

        assertTrue(registry.isRegistered("fresh"));
        verify(container).withNetwork(registry.network());
        verify(container).start();
    }

    @Test
    @DisplayName("registerAndStart should skip network attachment and start for an already-running container")
    void testRegisterAndStartWhenAlreadyRunning() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();

        @SuppressWarnings("unchecked")
        GenericContainer<?> container = mock(GenericContainer.class);
        when(container.isRunning()).thenReturn(true);

        registry.registerAndStart("running", container);

        assertTrue(registry.isRegistered("running"));
        verify(container, never()).withNetwork(any());
        verify(container, never()).start();
    }

    @Test
    @DisplayName("registerAndStart should start plain Startables that are not GenericContainers")
    void testRegisterAndStartPlainStartable() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        Startable startable = mock(Startable.class);

        registry.registerAndStart("plain", startable);

        assertTrue(registry.isRegistered("plain"));
        verify(startable).start();
    }

    @Test
    @DisplayName("stopAll should stop every container in reverse registration order and clear the registry")
    void testStopAllReverseOrder() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        List<String> stopOrder = new ArrayList<>();

        Startable a = mock(Startable.class);
        Startable b = mock(Startable.class);
        Startable c = mock(Startable.class);
        doAnswer(inv -> stopOrder.add("a")).when(a).stop();
        doAnswer(inv -> stopOrder.add("b")).when(b).stop();
        doAnswer(inv -> stopOrder.add("c")).when(c).stop();

        registry.register("a", a);
        registry.register("b", b);
        registry.register("c", c);

        registry.stopAll();

        assertEquals(List.of("c", "b", "a"), stopOrder);
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("stopAll should continue stopping other containers even if one throws")
    void testStopAllContinuesAfterFailure() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        Startable failing = mock(Startable.class);
        Startable healthy = mock(Startable.class);
        doThrow(new RuntimeException("boom")).when(failing).stop();

        registry.register("failing", failing);
        registry.register("healthy", healthy);

        assertDoesNotThrow(registry::stopAll);

        verify(failing).stop();
        verify(healthy).stop();
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("stopAll on an empty registry should be a no-op")
    void testStopAllEmpty() {
        TestContainerRegistry registry = TestContainerRegistry.getInstance();
        assertDoesNotThrow(registry::stopAll);
        assertEquals(0, registry.size());
    }

    // ---- reconciliation with TestContainerFacade ------------------------------------------

    private TestContainerFacade newFacade() throws Exception {
        Constructor<TestContainerFacade> ctor = TestContainerFacade.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    @Test
    @DisplayName("TestContainerFacade.startPostgres should register the container with the shared registry")
    void testFacadeRegistersWithSharedRegistry() throws Exception {
        TestContainerFacade facade = newFacade();

        try (MockedConstruction<PostgreSQLContainer> mocked = mockConstruction(PostgreSQLContainer.class,
                (mock, ctx) -> {
                    when(mock.withDatabaseName(anyString())).thenReturn(mock);
                    when(mock.withUsername(anyString())).thenReturn(mock);
                    when(mock.withPassword(anyString())).thenReturn(mock);
                    when(mock.withReuse(anyBoolean())).thenReturn(mock);
                    when(mock.getJdbcUrl()).thenReturn("jdbc:postgresql://localhost:5432/test");
                })) {

            facade.startPostgres();

            TestContainerRegistry registry = TestContainerRegistry.getInstance();
            assertTrue(registry.isRegistered("postgres"));

            facade.stop("postgres");
            assertFalse(registry.isRegistered("postgres"));
        }
    }
}
