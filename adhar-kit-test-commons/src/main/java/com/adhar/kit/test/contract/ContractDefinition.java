package com.adhar.kit.test.contract;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * A single provider/consumer contract, parsed from a WireMock-style stub-mapping JSON document.
 *
 * <p>The same document serves both sides of the contract:</p>
 * <ul>
 *   <li><b>Consumer side</b> - {@code ContractTestBase} loads the raw JSON straight into a WireMock
 *       server so the consumer under test can be exercised against the stubbed response.</li>
 *   <li><b>Provider side</b> - the parsed {@link #method()}/{@link #path()} identify the request to
 *       replay against the real provider, and {@link #expectedBody()} is the payload the provider's
 *       actual response is verified against with {@link JsonContractMatcher}.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public final class ContractDefinition {

    private final String name;
    private final String method;
    private final String path;
    private final int expectedStatus;
    private final JsonNode expectedBody;
    private final String rawJson;

    public ContractDefinition(String name, String method, String path, int expectedStatus,
                              JsonNode expectedBody, String rawJson) {
        this.name = name;
        this.method = method;
        this.path = path;
        this.expectedStatus = expectedStatus;
        this.expectedBody = expectedBody;
        this.rawJson = rawJson;
    }

    /** Logical contract name (defaults to the source file name without extension). */
    public String name() {
        return name;
    }

    /** HTTP method of the contract request (e.g. {@code GET}); {@code ANY} when unspecified. */
    public String method() {
        return method;
    }

    /** Request path/URL the contract applies to. */
    public String path() {
        return path;
    }

    /** Expected HTTP response status. */
    public int expectedStatus() {
        return expectedStatus;
    }

    /** Expected JSON response body, or {@code null} when the contract does not pin a body. */
    public JsonNode expectedBody() {
        return expectedBody;
    }

    /** Whether the contract declares an expected JSON body to verify against. */
    public boolean hasExpectedBody() {
        return expectedBody != null;
    }

    /** The raw WireMock stub-mapping JSON, for loading directly into a WireMock server. */
    public String rawJson() {
        return rawJson;
    }

    @Override
    public String toString() {
        return "ContractDefinition{name=" + name + ", method=" + method + ", path=" + path
                + ", expectedStatus=" + expectedStatus + "}";
    }
}
