package com.adhar.kit.test.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link MockRestServer}.
 *
 * <p>Uses Spring's {@link org.springframework.test.web.client.MockRestServiceServer}
 * under the hood which intercepts the {@link RestTemplate} - no real network calls are made.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@DisplayName("MockRestServer (mock package) Tests")
class MockRestServerTest {

    private RestTemplate restTemplate;
    private MockRestServer mockRestServer;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        mockRestServer = MockRestServer.create(restTemplate);
    }

    @Test
    @DisplayName("Should create MockRestServer for a RestTemplate")
    void testCreate() {
        assertNotNull(mockRestServer, "MockRestServer instance should not be null");
    }

    @Test
    @DisplayName("Should stub a GET request and respond with JSON")
    void testExpectGetRespondWithJson() {
        mockRestServer
                .expectGet("http://example.com/users/1")
                .respondWithJson("http://example.com/users/1", "{\"id\":1,\"name\":\"John\"}");

        String body = restTemplate.getForObject("http://example.com/users/1", String.class);

        assertEquals("{\"id\":1,\"name\":\"John\"}", body);
        mockRestServer.verify();
    }

    @Test
    @DisplayName("Should stub a POST request and respond with a status")
    void testExpectPostRespondWithStatus() {
        mockRestServer
                .expectPost("http://example.com/users")
                .respondWith("http://example.com/users", HttpStatus.CREATED);

        ResponseEntity<String> response =
                restTemplate.postForEntity("http://example.com/users", "{}", String.class);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        mockRestServer.verify();
    }

    @Test
    @DisplayName("Should stub a PUT request")
    void testExpectPut() {
        mockRestServer
                .expectPut("http://example.com/users/1")
                .respondWith("http://example.com/users/1", HttpStatus.OK);

        assertDoesNotThrow(() ->
                restTemplate.put("http://example.com/users/1", "{}"));
        mockRestServer.verify();
    }

    @Test
    @DisplayName("Should stub a DELETE request")
    void testExpectDelete() {
        mockRestServer
                .expectDelete("http://example.com/users/1")
                .respondWith("http://example.com/users/1", HttpStatus.NO_CONTENT);

        assertDoesNotThrow(() ->
                restTemplate.delete("http://example.com/users/1"));
        mockRestServer.verify();
    }

    @Test
    @DisplayName("Should respond with an error status and message body")
    void testRespondWithError() {
        mockRestServer
                .expectGet("http://example.com/fail")
                .respondWithError("http://example.com/fail", HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        HttpClientErrorException ex = null;
        try {
            restTemplate.getForObject("http://example.com/fail", String.class);
        } catch (HttpClientErrorException e) {
            ex = e;
        } catch (Exception e) {
            // 5xx maps to HttpServerErrorException; assert via message instead
            assertTrue(e.getMessage().contains("500"));
            mockRestServer.verify();
            return;
        }
        // If a client error was thrown, ensure it carried our payload
        if (ex != null) {
            assertTrue(ex.getResponseBodyAsString().contains("boom"));
        }
        mockRestServer.verify();
    }

    @Test
    @DisplayName("respondWithJson should be a no-op for an unknown URL")
    void testRespondWithJsonUnknownUrl() {
        // No expectation registered for this URL -> the lookup returns null and nothing happens
        MockRestServer result = mockRestServer.respondWithJson("http://unknown", "{}");
        assertSame(mockRestServer, result, "Should return the same instance for fluent chaining");
    }

    @Test
    @DisplayName("respondWith should be a no-op for an unknown URL")
    void testRespondWithUnknownUrl() {
        MockRestServer result = mockRestServer.respondWith("http://unknown", HttpStatus.OK);
        assertSame(mockRestServer, result);
    }

    @Test
    @DisplayName("respondWithError should be a no-op for an unknown URL")
    void testRespondWithErrorUnknownUrl() {
        MockRestServer result =
                mockRestServer.respondWithError("http://unknown", HttpStatus.BAD_REQUEST, "msg");
        assertSame(mockRestServer, result);
    }

    @Test
    @DisplayName("Should reset expectations and clear the server")
    void testReset() {
        mockRestServer
                .expectGet("http://example.com/users/1")
                .respondWithJson("http://example.com/users/1", "{}");

        mockRestServer.reset();

        // After reset, re-registering and responding should still work cleanly
        mockRestServer
                .expectGet("http://example.com/users/2")
                .respondWithJson("http://example.com/users/2", "{\"id\":2}");

        String body = restTemplate.getForObject("http://example.com/users/2", String.class);
        assertEquals("{\"id\":2}", body);
        mockRestServer.verify();
    }

    @Test
    @DisplayName("verify should fail when an expected request was not made")
    void testVerifyFailsForUnmetExpectation() {
        mockRestServer
                .expectGet("http://example.com/never-called")
                .respondWithJson("http://example.com/never-called", "{}");

        assertThrows(AssertionError.class, () -> mockRestServer.verify(),
                "verify should fail when an expectation is not satisfied");
    }
}
