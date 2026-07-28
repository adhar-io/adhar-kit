package com.adhar.kit.test.contract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ContractLoader}. Parsing is exercised directly; classpath loading reads the
 * fixtures under {@code src/test/resources/contracts}.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("ContractLoader Tests")
class ContractLoaderTest {

    private final ContractLoader loader = new ContractLoader();

    @Test
    @DisplayName("parse should extract method, path, status and jsonBody")
    void testParseJsonBody() {
        String json = "{\"request\":{\"method\":\"GET\",\"urlPath\":\"/api/x\"},"
                + "\"response\":{\"status\":200,\"jsonBody\":{\"id\":1}}}";

        ContractDefinition contract = loader.parse("x", json);

        assertEquals("x", contract.name());
        assertEquals("GET", contract.method());
        assertEquals("/api/x", contract.path());
        assertEquals(200, contract.expectedStatus());
        assertTrue(contract.hasExpectedBody());
        assertEquals(1, contract.expectedBody().get("id").asInt());
        assertEquals(json, contract.rawJson());
    }

    @Test
    @DisplayName("parse should parse a JSON string body")
    void testParseStringBody() {
        String json = "{\"request\":{\"method\":\"GET\",\"url\":\"/y\"},"
                + "\"response\":{\"status\":200,\"body\":\"{\\\"ok\\\":true}\"}}";

        ContractDefinition contract = loader.parse("y", json);

        assertEquals("/y", contract.path());
        assertTrue(contract.hasExpectedBody());
        assertTrue(contract.expectedBody().get("ok").asBoolean());
    }

    @Test
    @DisplayName("parse should default method to ANY and status to 200 when omitted")
    void testParseDefaults() {
        String json = "{\"request\":{\"urlPath\":\"/z\"},\"response\":{}}";

        ContractDefinition contract = loader.parse("z", json);

        assertEquals("ANY", contract.method());
        assertEquals(200, contract.expectedStatus());
        assertFalse(contract.hasExpectedBody());
        assertNull(contract.expectedBody());
    }

    @Test
    @DisplayName("parse should leave body null when response.body is not JSON")
    void testParseNonJsonBody() {
        String json = "{\"request\":{\"urlPath\":\"/z\"},\"response\":{\"body\":\"plain text\"}}";

        ContractDefinition contract = loader.parse("z", json);

        assertFalse(contract.hasExpectedBody());
    }

    @Test
    @DisplayName("parse should throw on malformed JSON")
    void testParseMalformed() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse("bad", "{not json"));
    }

    @Test
    @DisplayName("parse should throw when the document is not a JSON object")
    void testParseNonObject() {
        assertThrows(IllegalArgumentException.class, () -> loader.parse("arr", "[1,2,3]"));
    }

    @Test
    @DisplayName("loadFromClasspath should load and sort every contract in the directory")
    void testLoadFromClasspath() {
        List<ContractDefinition> contracts = loader.loadFromClasspath("contracts");

        assertEquals(2, contracts.size());
        // sorted by file name: user-create.json before user-get.json
        assertEquals("user-create", contracts.get(0).name());
        assertEquals("user-get", contracts.get(1).name());
        assertEquals(201, contracts.get(0).expectedStatus());
        assertEquals("GET", contracts.get(1).method());
    }

    @Test
    @DisplayName("loadFromClasspath should throw for a missing directory")
    void testLoadFromClasspathMissing() {
        assertThrows(IllegalArgumentException.class, () -> loader.loadFromClasspath("does-not-exist"));
    }
}
