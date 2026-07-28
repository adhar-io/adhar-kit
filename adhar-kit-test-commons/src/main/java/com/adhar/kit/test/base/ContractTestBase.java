package com.adhar.kit.test.base;

import com.adhar.kit.test.contract.ContractDefinition;
import com.adhar.kit.test.contract.ContractLoader;
import com.adhar.kit.test.contract.ContractVerificationResult;
import com.adhar.kit.test.contract.JsonContractMatcher;
import com.adhar.kit.test.wiremock.WireMockTestServer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WireMock-backed base class for pragmatic provider/consumer contract tests, with <b>no Pact</b>
 * (or other contract framework) dependency.
 *
 * <p>A single embedded {@link WireMockTestServer} is started once per test class. Subclasses call
 * {@link #loadContracts(String)} to read WireMock-style stub-mapping JSON from a contracts directory
 * on the classpath; each contract is:</p>
 * <ul>
 *   <li>registered as a stub on the WireMock server, so a <b>consumer</b> pointed at
 *       {@link #contractBaseUrl()} is exercised against the contracted responses; and</li>
 *   <li>retained by name so a <b>provider's</b> real response can be checked against the contracted
 *       body with {@link #verifyResponse(String, String)} (JSON field or schema assertions via
 *       {@link JsonContractMatcher}).</li>
 * </ul>
 *
 * <p>Being embedded (like {@link WireMockIntegrationTest}) and not Testcontainers-backed, this base
 * needs no Docker.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public abstract class ContractTestBase {

    protected static WireMockTestServer contractServer;

    private final ContractLoader contractLoader = new ContractLoader();
    private final JsonContractMatcher contractMatcher = new JsonContractMatcher();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ContractDefinition> contracts = new LinkedHashMap<>();

    @BeforeAll
    static void startContractServer() {
        contractServer = WireMockTestServer.start();
    }

    @AfterAll
    static void stopContractServer() {
        if (contractServer != null) {
            contractServer.stop();
        }
    }

    @AfterEach
    void resetContractState() {
        if (contractServer != null) {
            contractServer.resetAll();
        }
        contracts.clear();
    }

    /**
     * Base URL of the embedded contract server; point the consumer under test at this.
     */
    protected String contractBaseUrl() {
        return contractServer.baseUrl();
    }

    /**
     * Load every contract from a classpath directory, register each as a WireMock stub, and retain
     * them by {@link ContractDefinition#name() name} for later provider verification.
     *
     * @param classpathDirectory e.g. {@code "contracts"} for {@code src/test/resources/contracts}
     * @return the loaded contracts
     */
    protected List<ContractDefinition> loadContracts(String classpathDirectory) {
        List<ContractDefinition> loaded = contractLoader.loadFromClasspath(classpathDirectory);
        loaded.forEach(this::registerContract);
        return loaded;
    }

    /**
     * Register a single already-parsed contract as a WireMock stub and retain it by name.
     */
    protected void registerContract(ContractDefinition contract) {
        contracts.put(contract.name(), contract);
        if (contractServer != null) {
            try {
                StubMapping mapping = StubMapping.buildFrom(contract.rawJson());
                contractServer.rawServer().addStubMapping(mapping);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Contract '" + contract.name() + "' is not a valid WireMock stub mapping", e);
            }
        }
    }

    /**
     * A previously loaded contract by name.
     */
    protected ContractDefinition contract(String name) {
        ContractDefinition contract = contracts.get(name);
        if (contract == null) {
            throw new IllegalArgumentException("No contract loaded with name '" + name + "'");
        }
        return contract;
    }

    /**
     * Verify an actual provider JSON response against the named contract's expected body using
     * <b>value</b> matching (fields present in the contract must match). Returns a result; call
     * {@link ContractVerificationResult#assertMatched()} to fail the test on mismatch.
     */
    protected ContractVerificationResult verifyResponse(String contractName, String actualJson) {
        ContractDefinition contract = contract(contractName);
        if (!contract.hasExpectedBody()) {
            throw new IllegalStateException("Contract '" + contractName + "' declares no expected body to verify");
        }
        return contractMatcher.verify(contract.expectedBody(), readTree(actualJson));
    }

    /**
     * Verify an actual provider JSON response against the named contract's expected body using
     * <b>schema</b> matching (structure and types only).
     */
    protected ContractVerificationResult verifyResponseSchema(String contractName, String actualJson) {
        ContractDefinition contract = contract(contractName);
        if (!contract.hasExpectedBody()) {
            throw new IllegalStateException("Contract '" + contractName + "' declares no expected body to verify");
        }
        return contractMatcher.verifySchema(contract.expectedBody(), readTree(actualJson));
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new UncheckedIOException("Actual response is not valid JSON", e);
        }
    }
}
