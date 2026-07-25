package com.adhar.kit.test.wiremock;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import lombok.extern.slf4j.Slf4j;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

/**
 * Richer HTTP stubbing helper built on top of WireMock, going beyond what
 * {@link com.adhar.kit.test.mock.MockRestServer} offers (which only wraps Spring's
 * {@code MockRestServiceServer} around a single {@code RestTemplate}).
 *
 * <p>Unlike the package-private WireMock helper in {@code CustomAssertions}, this class is
 * instance-based and never touches WireMock's global static default client, so multiple
 * independent servers can be run side by side (e.g. to simulate several downstream services
 * in one test) without stepping on each other.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * WireMockTestServer server = WireMockTestServer.start();
 * server.stubGetJson("/api/users/1", 200, "{\"id\":1}");
 *
 * // call the service under test, pointed at server.baseUrl() ...
 *
 * server.verifyGetCalled("/api/users/1", 1);
 * server.stop();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class WireMockTestServer {

    private final WireMockServer server;

    private WireMockTestServer(WireMockServer server) {
        this.server = server;
    }

    /**
     * Start a WireMock server on a random free port.
     */
    public static WireMockTestServer start() {
        return start(0);
    }

    /**
     * Start a WireMock server on the given port, or a random free port if {@code port <= 0}.
     *
     * <p>The server is bound explicitly to the {@code 127.0.0.1} loopback address rather than
     * the default wildcard bind. This matters because {@link #baseUrl()} also targets
     * {@code 127.0.0.1} explicitly instead of {@code localhost}: on a dual-stack host,
     * resolving {@code localhost} can race between the IPv4 and IPv6 loopback addresses, and an
     * unrelated local process listening on the same port number on the other address family can
     * intercept the request. Pinning both the server bind address and the client-facing base URL
     * to the same concrete IPv4 address avoids that race entirely.</p>
     */
    public static WireMockTestServer start(int port) {
        WireMockConfiguration config = WireMockConfiguration.options().bindAddress("127.0.0.1");
        if (port > 0) {
            config.port(port);
        } else {
            config.dynamicPort();
        }

        WireMockServer server = new WireMockServer(config);
        server.start();
        log.info("WireMockTestServer started on port {}", server.port());
        return new WireMockTestServer(server);
    }

    /**
     * The port the server is bound to.
     */
    public int port() {
        return server.port();
    }

    /**
     * The base URL of this server, e.g. {@code http://127.0.0.1:8080}. Deliberately uses the
     * concrete loopback address rather than {@code localhost} - see {@link #start(int)}.
     */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.port();
    }

    /**
     * Whether the underlying server is currently running.
     */
    public boolean isRunning() {
        return server.isRunning();
    }

    /**
     * Escape hatch to the underlying {@link WireMockServer} for anything not exposed here
     * (scenarios, request matching by body pattern, fault injection, etc.).
     */
    public WireMockServer rawServer() {
        return server;
    }

    // ---- stub registration --------------------------------------------------

    /**
     * Stub a GET request to {@code urlPath}, responding with the given status and a
     * JSON body (Content-Type: application/json).
     */
    public StubMapping stubGetJson(String urlPath, int status, String jsonBody) {
        return stub(get(urlPathEqualTo(urlPath)), status, jsonBody, "application/json");
    }

    /**
     * Stub a GET request to {@code urlPath}, responding with the given status/body/content-type.
     */
    public StubMapping stubGet(String urlPath, int status, String body, String contentType) {
        return stub(get(urlPathEqualTo(urlPath)), status, body, contentType);
    }

    /**
     * Stub a POST request to {@code urlPath}, responding with the given status and JSON body.
     */
    public StubMapping stubPostJson(String urlPath, int status, String jsonResponseBody) {
        return stub(post(urlPathEqualTo(urlPath)), status, jsonResponseBody, "application/json");
    }

    /**
     * Stub a PUT request to {@code urlPath}, responding with the given status and JSON body.
     */
    public StubMapping stubPutJson(String urlPath, int status, String jsonResponseBody) {
        return stub(put(urlPathEqualTo(urlPath)), status, jsonResponseBody, "application/json");
    }

    /**
     * Stub a DELETE request to {@code urlPath}, responding with the given status and empty body.
     */
    public StubMapping stubDelete(String urlPath, int status) {
        return stub(delete(urlPathEqualTo(urlPath)), status, "", "application/json");
    }

    /**
     * Stub a GET request to {@code urlPath} that responds after a fixed delay - useful for
     * exercising timeout/retry handling in the code under test.
     */
    public StubMapping stubGetWithDelay(String urlPath, int status, String body, int delayMillis) {
        ResponseDefinitionBuilder response = aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body)
                .withFixedDelay(delayMillis);
        return server.stubFor(get(urlPathEqualTo(urlPath)).willReturn(response));
    }

    /**
     * Stub a GET request to {@code urlPath} that responds with an extra header alongside
     * status/body.
     */
    public StubMapping stubGetWithHeader(String urlPath, int status, String body, String headerName, String headerValue) {
        ResponseDefinitionBuilder response = aResponse()
                .withStatus(status)
                .withHeader(headerName, headerValue)
                .withBody(body);
        return server.stubFor(get(urlPathEqualTo(urlPath)).willReturn(response));
    }

    /**
     * Register a fully custom stub mapping for callers that need matching beyond the
     * convenience methods above (query params, headers, request body patterns, scenarios, etc.).
     */
    public StubMapping stub(MappingBuilder mappingBuilder) {
        return server.stubFor(mappingBuilder);
    }

    private StubMapping stub(MappingBuilder mappingBuilder, int status, String body, String contentType) {
        ResponseDefinitionBuilder response = aResponse()
                .withStatus(status)
                .withHeader("Content-Type", contentType)
                .withBody(body);
        return server.stubFor(mappingBuilder.willReturn(response));
    }

    // ---- verification ---------------------------------------------------------

    /**
     * Verify that GET was called on {@code urlPath} exactly {@code times} times.
     */
    public void verifyGetCalled(String urlPath, int times) {
        server.verify(times, getRequestedFor(urlEqualTo(urlPath)));
    }

    /**
     * Verify that POST was called on {@code urlPath} exactly {@code times} times.
     */
    public void verifyPostCalled(String urlPath, int times) {
        server.verify(times, postRequestedFor(urlEqualTo(urlPath)));
    }

    /**
     * Verify that PUT was called on {@code urlPath} exactly {@code times} times.
     */
    public void verifyPutCalled(String urlPath, int times) {
        server.verify(times, putRequestedFor(urlEqualTo(urlPath)));
    }

    /**
     * Verify that DELETE was called on {@code urlPath} exactly {@code times} times.
     */
    public void verifyDeleteCalled(String urlPath, int times) {
        server.verify(times, deleteRequestedFor(urlEqualTo(urlPath)));
    }

    /**
     * Verify against a fully custom request pattern for callers that need more than
     * URL + method matching.
     */
    public void verify(int times, RequestPatternBuilder pattern) {
        server.verify(times, pattern);
    }

    /**
     * How many requests have been served that match the given URL, regardless of method.
     */
    public int countRequestsTo(String urlPath) {
        return server.findAll(getRequestedFor(urlEqualTo(urlPath))).size()
                + server.findAll(postRequestedFor(urlEqualTo(urlPath))).size()
                + server.findAll(putRequestedFor(urlEqualTo(urlPath))).size()
                + server.findAll(deleteRequestedFor(urlEqualTo(urlPath))).size();
    }

    // ---- lifecycle --------------------------------------------------------------

    /**
     * Reset both stub mappings and recorded requests.
     */
    public void resetAll() {
        server.resetAll();
    }

    /**
     * Reset only stub mappings, keeping recorded request history.
     */
    public void resetMappings() {
        server.resetMappings();
    }

    /**
     * Stop the server if it is running.
     */
    public void stop() {
        if (server.isRunning()) {
            server.stop();
            log.info("WireMockTestServer stopped");
        }
    }
}
