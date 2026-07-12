package com.adhar.kit.test.assertion;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the package-private WireMock {@code MockRestServer} declared in
 * {@code CustomAssertions.java}.
 *
 * <p>WireMock construction is intercepted with {@link MockedConstruction} so that
 * no real HTTP server is ever bound (pure unit test, no network).</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("Assertion MockRestServer (WireMock) Tests")
class AssertionMockRestServerTest {

    private void setServerField(WireMockServer server) throws Exception {
        Field f = MockRestServer.class.getDeclaredField("wireMockServer");
        f.setAccessible(true);
        f.set(null, server);
    }

    @BeforeEach
    @AfterEach
    void resetField() throws Exception {
        setServerField(null);
    }

    @Test
    @DisplayName("Should start WireMock on a dynamic port")
    void testStartDynamicPort() {
        try (MockedConstruction<WireMockServer> mocked = mockConstruction(WireMockServer.class,
                (mock, ctx) -> {
                    when(mock.isRunning()).thenReturn(true);
                    when(mock.port()).thenReturn(12345);
                })) {

            MockRestServer.start();

            assertEquals(1, mocked.constructed().size(), "Exactly one WireMockServer should be created");
            WireMockServer server = MockRestServer.getInstance();
            assertNotNull(server);
            verify(server).start();
            assertEquals(12345, MockRestServer.getPort());
        }
    }

    @Test
    @DisplayName("Should start WireMock on a fixed port")
    void testStartFixedPort() {
        try (MockedConstruction<WireMockServer> mocked = mockConstruction(WireMockServer.class,
                (mock, ctx) -> {
                    when(mock.isRunning()).thenReturn(true);
                    when(mock.port()).thenReturn(9999);
                })) {

            MockRestServer.start(9999);

            assertEquals(1, mocked.constructed().size());
            assertEquals(9999, MockRestServer.getPort());
            assertEquals("http://localhost:9999", MockRestServer.getBaseUrl());
        }
    }

    @Test
    @DisplayName("Should not recreate the server when already running")
    void testStartWhenAlreadyRunning() throws Exception {
        WireMockServer existing = mock(WireMockServer.class);
        when(existing.isRunning()).thenReturn(true);
        setServerField(existing);

        try (MockedConstruction<WireMockServer> mocked = mockConstruction(WireMockServer.class)) {
            MockRestServer.start();
            assertTrue(mocked.constructed().isEmpty(),
                    "No new server should be constructed when one is already running");
        }
        verify(existing, never()).start();
    }

    @Test
    @DisplayName("getInstance should return null before start")
    void testGetInstanceNull() {
        assertNull(MockRestServer.getInstance(), "Instance should be null before start");
    }

    @Test
    @DisplayName("getInstance should return the running server")
    void testGetInstanceReturnsServer() throws Exception {
        WireMockServer server = mock(WireMockServer.class);
        setServerField(server);
        assertSame(server, MockRestServer.getInstance());
    }

    @Test
    @DisplayName("getPort should throw when server is null")
    void testGetPortThrowsWhenNull() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, MockRestServer::getPort);
        assertTrue(ex.getMessage().contains("not running"));
    }

    @Test
    @DisplayName("getPort should throw when server is not running")
    void testGetPortThrowsWhenNotRunning() throws Exception {
        WireMockServer server = mock(WireMockServer.class);
        when(server.isRunning()).thenReturn(false);
        setServerField(server);

        assertThrows(IllegalStateException.class, MockRestServer::getPort);
    }

    @Test
    @DisplayName("getBaseUrl should build a localhost URL from the port")
    void testGetBaseUrl() throws Exception {
        WireMockServer server = mock(WireMockServer.class);
        when(server.isRunning()).thenReturn(true);
        when(server.port()).thenReturn(7070);
        setServerField(server);

        assertEquals("http://localhost:7070", MockRestServer.getBaseUrl());
    }

    @Test
    @DisplayName("stop should stop a running server")
    void testStopRunningServer() throws Exception {
        WireMockServer server = mock(WireMockServer.class);
        when(server.isRunning()).thenReturn(true);
        setServerField(server);

        MockRestServer.stop();

        verify(server).stop();
    }

    @Test
    @DisplayName("stop should be a no-op when server is null")
    void testStopWhenNull() {
        assertDoesNotThrow(MockRestServer::stop);
    }

    @Test
    @DisplayName("stop should be a no-op when not running")
    void testStopWhenNotRunning() throws Exception {
        WireMockServer server = mock(WireMockServer.class);
        when(server.isRunning()).thenReturn(false);
        setServerField(server);

        MockRestServer.stop();

        verify(server, never()).stop();
    }

    @Test
    @DisplayName("reset should reset a running server's stubs")
    void testResetRunningServer() throws Exception {
        WireMockServer server = mock(WireMockServer.class);
        when(server.isRunning()).thenReturn(true);
        setServerField(server);

        MockRestServer.reset();

        verify(server).resetAll();
    }

    @Test
    @DisplayName("reset should be a no-op when server is null")
    void testResetWhenNull() {
        assertDoesNotThrow(MockRestServer::reset);
    }

    @Test
    @DisplayName("reset should be a no-op when not running")
    void testResetWhenNotRunning() throws Exception {
        WireMockServer server = mock(WireMockServer.class);
        when(server.isRunning()).thenReturn(false);
        setServerField(server);

        MockRestServer.reset();

        verify(server, never()).resetAll();
    }
}
