package com.adhar.kit.test.wiremock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.ServerSocket;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link WireMockTestServer}.
 *
 * <p>WireMock is an embedded HTTP server (unlike the Testcontainers-backed helpers in this
 * module) so these tests start a real server bound to a real port and make real HTTP calls
 * with {@link RestTemplate} - no Docker is required.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@DisplayName("WireMockTestServer Tests")
class WireMockTestServerTest {

    private WireMockTestServer server;
    private final RestTemplate restTemplate = new RestTemplate();

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException("Could not find a free port", e);
        }
    }

    @Test
    @DisplayName("start() should bind to a dynamic port and report it as running")
    void testStartDynamicPort() {
        server = WireMockTestServer.start();

        assertTrue(server.isRunning());
        assertTrue(server.port() > 0);
        assertEquals("http://127.0.0.1:" + server.port(), server.baseUrl());
        assertNotNull(server.rawServer());
    }

    @Test
    @DisplayName("start(port) should bind to the requested port")
    void testStartFixedPort() {
        int freePort = findFreePort();
        server = WireMockTestServer.start(freePort);

        assertEquals(freePort, server.port());
        assertTrue(server.isRunning());
    }

    @Test
    @DisplayName("stubGetJson should serve the stubbed status/body for a matching GET")
    void testStubGetJson() {
        server = WireMockTestServer.start();
        server.stubGetJson("/api/users/1", 200, "{\"id\":1,\"name\":\"John\"}");

        ResponseEntity<String> response =
                restTemplate.getForEntity(server.baseUrl() + "/api/users/1", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("{\"id\":1,\"name\":\"John\"}", response.getBody());
        server.verifyGetCalled("/api/users/1", 1);
    }

    @Test
    @DisplayName("stubPostJson should serve the stubbed response for a matching POST")
    void testStubPostJson() {
        server = WireMockTestServer.start();
        server.stubPostJson("/api/users", 201, "{\"id\":2}");

        ResponseEntity<String> response =
                restTemplate.postForEntity(server.baseUrl() + "/api/users", "{}", String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals("{\"id\":2}", response.getBody());
        server.verifyPostCalled("/api/users", 1);
    }

    @Test
    @DisplayName("stubPutJson should serve the stubbed response for a matching PUT")
    void testStubPutJson() {
        server = WireMockTestServer.start();
        server.stubPutJson("/api/users/1", 200, "{\"id\":1,\"name\":\"updated\"}");

        ResponseEntity<String> response = restTemplate.exchange(
                server.baseUrl() + "/api/users/1", HttpMethod.PUT, new HttpEntity<>("{}"), String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        server.verifyPutCalled("/api/users/1", 1);
    }

    @Test
    @DisplayName("stubDelete should serve the stubbed status for a matching DELETE")
    void testStubDelete() {
        server = WireMockTestServer.start();
        server.stubDelete("/api/users/1", 204);

        ResponseEntity<Void> response = restTemplate.exchange(
                server.baseUrl() + "/api/users/1", HttpMethod.DELETE, HttpEntity.EMPTY, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        server.verifyDeleteCalled("/api/users/1", 1);
    }

    @Test
    @DisplayName("stubGetWithHeader should include the extra header in the response")
    void testStubGetWithHeader() {
        server = WireMockTestServer.start();
        server.stubGetWithHeader("/api/ping", 200, "pong", "X-Custom-Header", "custom-value");

        ResponseEntity<String> response =
                restTemplate.getForEntity(server.baseUrl() + "/api/ping", String.class);

        assertEquals("pong", response.getBody());
        assertEquals("custom-value", response.getHeaders().getFirst("X-Custom-Header"));
    }

    @Test
    @DisplayName("stubGetWithDelay should still serve the stubbed response")
    void testStubGetWithDelay() {
        server = WireMockTestServer.start();
        server.stubGetWithDelay("/api/slow", 200, "done", 50);

        ResponseEntity<String> response =
                restTemplate.getForEntity(server.baseUrl() + "/api/slow", String.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("done", response.getBody());
    }

    @Test
    @DisplayName("stub(MappingBuilder) and verify(int, pattern) should support fully custom mappings")
    void testCustomStubAndVerify() {
        server = WireMockTestServer.start();
        server.stub(get("/api/custom").willReturn(
                com.github.tomakehurst.wiremock.client.WireMock.aResponse().withStatus(200).withBody("custom")));

        ResponseEntity<String> response =
                restTemplate.getForEntity(server.baseUrl() + "/api/custom", String.class);

        assertEquals("custom", response.getBody());
        server.verify(1, getRequestedFor(urlEqualTo("/api/custom")));
    }

    @Test
    @DisplayName("countRequestsTo should count served requests across methods")
    void testCountRequestsTo() {
        server = WireMockTestServer.start();
        server.stubGetJson("/api/count", 200, "{}");

        assertEquals(0, server.countRequestsTo("/api/count"));

        restTemplate.getForEntity(server.baseUrl() + "/api/count", String.class);
        restTemplate.getForEntity(server.baseUrl() + "/api/count", String.class);

        assertEquals(2, server.countRequestsTo("/api/count"));
    }

    @Test
    @DisplayName("resetAll should clear both stubs and recorded requests")
    void testResetAll() {
        server = WireMockTestServer.start();
        server.stubGetJson("/api/reset-me", 200, "{}");
        restTemplate.getForEntity(server.baseUrl() + "/api/reset-me", String.class);
        assertEquals(1, server.countRequestsTo("/api/reset-me"));

        server.resetAll();

        assertEquals(0, server.countRequestsTo("/api/reset-me"));
        assertThrows(HttpClientErrorException.NotFound.class,
                () -> restTemplate.getForEntity(server.baseUrl() + "/api/reset-me", String.class));
    }

    @Test
    @DisplayName("resetMappings should clear stubs but not necessarily fail on unmatched requests")
    void testResetMappings() {
        server = WireMockTestServer.start();
        server.stubGetJson("/api/mapping-reset", 200, "{}");

        server.resetMappings();

        assertThrows(HttpClientErrorException.NotFound.class,
                () -> restTemplate.getForEntity(server.baseUrl() + "/api/mapping-reset", String.class));
    }

    @Test
    @DisplayName("verifyGetCalled should throw when the expected call count was not met")
    void testVerifyFailsForUnmetExpectation() {
        server = WireMockTestServer.start();
        server.stubGetJson("/api/never-called", 200, "{}");

        assertThrows(AssertionError.class, () -> server.verifyGetCalled("/api/never-called", 1));
    }

    @Test
    @DisplayName("stop should be idempotent")
    void testStopIdempotent() {
        server = WireMockTestServer.start();
        server.stop();
        assertFalse(server.isRunning());
        assertDoesNotThrow(() -> server.stop());
    }
}
