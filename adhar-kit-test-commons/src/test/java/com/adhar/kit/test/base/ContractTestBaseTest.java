package com.adhar.kit.test.base;

import com.adhar.kit.test.contract.ContractDefinition;
import com.adhar.kit.test.contract.ContractVerificationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ContractTestBase}, exercising it as a real subclass. WireMock is embedded, so
 * no Docker is required: contracts are loaded from {@code src/test/resources/contracts}, served by
 * the embedded server, hit with {@link RestTemplate} (consumer side) and verified against actual
 * payloads (provider side).
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@DisplayName("ContractTestBase Tests")
class ContractTestBaseTest extends ContractTestBase {

    private final RestTemplate restTemplate = new RestTemplate();

    @Test
    @DisplayName("loadContracts should stub every contract so the consumer sees the contracted response")
    void testConsumerSideStubbing() {
        List<ContractDefinition> contracts = loadContracts("contracts");
        assertEquals(2, contracts.size());

        ResponseEntity<String> response =
                restTemplate.getForEntity(contractBaseUrl() + "/api/users/1", String.class);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().contains("\"name\":\"John\""));
    }

    @Test
    @DisplayName("verifyResponse should pass when the provider response matches the contract body")
    void testProviderVerificationPasses() {
        loadContracts("contracts");

        String actual = "{\"id\":1,\"name\":\"John\",\"active\":true,\"extra\":\"ok\"}";
        ContractVerificationResult result = verifyResponse("user-get", actual);

        assertTrue(result.matched());
    }

    @Test
    @DisplayName("verifyResponse should fail when the provider response diverges from the contract")
    void testProviderVerificationFails() {
        loadContracts("contracts");

        String actual = "{\"id\":99,\"name\":\"John\",\"active\":true}";
        ContractVerificationResult result = verifyResponse("user-get", actual);

        assertFalse(result.matched());
        assertThrows(AssertionError.class, result::assertMatched);
    }

    @Test
    @DisplayName("verifyResponseSchema should pass when structure matches even if values differ")
    void testProviderSchemaVerification() {
        loadContracts("contracts");

        String actual = "{\"id\":42,\"name\":\"Somebody\",\"active\":false}";
        assertTrue(verifyResponseSchema("user-get", actual).matched());
    }

    @Test
    @DisplayName("contract lookup should throw for an unknown name")
    void testUnknownContract() {
        loadContracts("contracts");
        assertThrows(IllegalArgumentException.class, () -> verifyResponse("nope", "{}"));
    }
}
