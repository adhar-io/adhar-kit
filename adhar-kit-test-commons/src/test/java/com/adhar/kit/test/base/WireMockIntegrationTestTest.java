package com.adhar.kit.test.base;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WireMockIntegrationTest}.
 *
 * <p>The static hooks are invoked directly (matching the pattern used by
 * {@code BaseIntegrationTestTest}), starting a real embedded WireMock server - no Docker
 * required.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@DisplayName("WireMockIntegrationTest Tests")
class WireMockIntegrationTestTest {

    private static class CapturingRegistry implements DynamicPropertyRegistry {
        final Map<String, Supplier<Object>> props = new LinkedHashMap<>();

        @Override
        public void add(String name, Supplier<Object> valueSupplier) {
            props.put(name, valueSupplier);
        }
    }

    /** Minimal concrete subclass to access the protected instance hook. */
    private static class ConcreteWireMockTest extends WireMockIntegrationTest {
    }

    private void resetStaticField() throws Exception {
        Field f = WireMockIntegrationTest.class.getDeclaredField("wireMockServer");
        f.setAccessible(true);
        f.set(null, null);
    }

    @AfterEach
    void tearDown() throws Exception {
        WireMockIntegrationTest.stopWireMockServer();
        resetStaticField();
    }

    @Test
    @DisplayName("Should be instantiable as a concrete subclass")
    void testInstantiable() {
        assertNotNull(new ConcreteWireMockTest());
    }

    @Test
    @DisplayName("startWireMockServer should start a running server")
    void testStartWireMockServer() {
        WireMockIntegrationTest.startWireMockServer();

        assertNotNull(new ConcreteWireMockTest());
        Object server = getWireMockServerField();
        assertNotNull(server);
    }

    @Test
    @DisplayName("configureWireMockProperties should publish the server base URL")
    void testConfigureWireMockProperties() {
        WireMockIntegrationTest.startWireMockServer();
        CapturingRegistry registry = new CapturingRegistry();

        WireMockIntegrationTest.configureWireMockProperties(registry);

        assertTrue(registry.props.containsKey("wiremock.server.base-url"));
        String baseUrl = (String) registry.props.get("wiremock.server.base-url").get();
        assertTrue(baseUrl.startsWith("http://127.0.0.1:"));
    }

    @Test
    @DisplayName("resetWireMockStubs should clear stubs registered on the shared server")
    void testResetWireMockStubs() throws Exception {
        WireMockIntegrationTest.startWireMockServer();
        com.adhar.kit.test.wiremock.WireMockTestServer server =
                (com.adhar.kit.test.wiremock.WireMockTestServer) getWireMockServerField();
        server.stubGetJson("/api/reset-test", 200, "{}");

        ConcreteWireMockTest subject = new ConcreteWireMockTest();
        subject.resetWireMockStubs();

        RestTemplate restTemplate = new RestTemplate();
        assertThrows(HttpClientErrorException.NotFound.class,
                () -> restTemplate.getForEntity(server.baseUrl() + "/api/reset-test", String.class));
    }

    @Test
    @DisplayName("stopWireMockServer should stop a running server")
    void testStopWireMockServer() throws Exception {
        WireMockIntegrationTest.startWireMockServer();
        com.adhar.kit.test.wiremock.WireMockTestServer server =
                (com.adhar.kit.test.wiremock.WireMockTestServer) getWireMockServerField();

        WireMockIntegrationTest.stopWireMockServer();

        assertFalse(server.isRunning());
    }

    @Test
    @DisplayName("stopWireMockServer should be a no-op when never started")
    void testStopWireMockServerNeverStarted() {
        assertDoesNotThrow(WireMockIntegrationTest::stopWireMockServer);
    }

    private Object getWireMockServerField() {
        try {
            Field f = WireMockIntegrationTest.class.getDeclaredField("wireMockServer");
            f.setAccessible(true);
            return f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
