package com.adhar.kit.analytics.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies {@link OkHttpPostHogClient} routes each operation to the correct
 * PostHog endpoint with a correctly-shaped payload. Uses a local
 * {@link MockWebServer} - no real network egress.
 */
@DisplayName("OkHttpPostHogClient Tests")
class OkHttpPostHogClientTest {

    private MockWebServer server;
    private OkHttpPostHogClient client;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        objectMapper = new ObjectMapper();
        client = new OkHttpPostHogClient(new OkHttpClient(), objectMapper, "phc_test_key", server.url("/").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        server.shutdown();
    }

    @Test
    @DisplayName("capture() posts to /capture/ with event, distinct_id and api_key")
    void captureRoutesToCaptureEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1}"));

        client.capture(CaptureEvent.of("Signed Up", "user-1", Map.of("plan", "pro")));

        RecordedRequest request = server.takeRequest();
        assertEquals("/capture/", request.getPath());
        assertEquals("POST", request.getMethod());

        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertEquals("phc_test_key", body.get("api_key").asText());
        assertEquals("Signed Up", body.get("event").asText());
        assertEquals("user-1", body.get("properties").get("distinct_id").asText());
        assertEquals("pro", body.get("properties").get("plan").asText());
    }

    @Test
    @DisplayName("batch() posts to /batch/ with a batch array, one call for all events")
    void batchRoutesToBatchEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("{\"status\":1}"));

        client.batch(List.of(
                CaptureEvent.of("Event A", "user-1", Map.of("x", 1)),
                CaptureEvent.of("Event B", "user-2", Map.of("y", 2))
        ));

        assertEquals(1, server.getRequestCount());
        RecordedRequest request = server.takeRequest();
        assertEquals("/batch/", request.getPath());

        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertEquals("phc_test_key", body.get("api_key").asText());
        assertEquals(2, body.get("batch").size());
        assertEquals("Event A", body.get("batch").get(0).get("event").asText());
        assertEquals("user-2", body.get("batch").get(1).get("properties").get("distinct_id").asText());
    }

    @Test
    @DisplayName("batch() with no events makes no HTTP call")
    void batchWithNoEventsNoOps() {
        client.batch(List.of());
        client.batch(null);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    @DisplayName("decide() posts to /decide/?v=3 and parses featureFlags")
    void decideRoutesToDecideEndpoint() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200)
                .setBody("{\"featureFlags\": {\"new-ui\": true, \"variant-flag\": \"blue\"}}"));

        DecideResult result = client.decide("user-1");

        RecordedRequest request = server.takeRequest();
        assertEquals("/decide/?v=3", request.getPath());
        assertEquals(Boolean.TRUE, result.featureFlags().get("new-ui"));
        assertEquals("blue", result.featureFlags().get("variant-flag"));
    }

    @Test
    @DisplayName("decide() returns empty result on non-2xx response")
    void decideReturnsEmptyOnFailure() {
        server.enqueue(new MockResponse().setResponseCode(500));

        DecideResult result = client.decide("user-1");

        assertTrue(result.featureFlags().isEmpty());
    }

    @Test
    @DisplayName("capture()/batch() log and swallow non-2xx responses rather than throw")
    void captureSwallowsServerErrors() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertDoesNotThrow(() -> client.capture(CaptureEvent.of("Event", "user-1", Map.of())));
    }

    @Test
    @DisplayName("decide() swallows connection failures and returns empty")
    void decideSwallowsConnectionFailure() throws IOException {
        // Shut the server down first so the request fails at the transport layer.
        server.shutdown();

        DecideResult result = client.decide("user-1");

        assertTrue(result.featureFlags().isEmpty());
    }

    @Test
    @DisplayName("capture() sets a non-null timestamp field")
    void captureIncludesTimestamp() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        client.capture(new CaptureEvent("Event", "user-1", Map.of(), Instant.parse("2024-01-01T00:00:00Z")));

        RecordedRequest request = server.takeRequest();
        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        assertEquals("2024-01-01T00:00:00Z", body.get("timestamp").asText());
    }
}
