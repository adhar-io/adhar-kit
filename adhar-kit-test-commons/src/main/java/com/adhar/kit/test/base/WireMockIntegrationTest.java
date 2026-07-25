package com.adhar.kit.test.base;

import com.adhar.kit.test.wiremock.WireMockTestServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that stub an external HTTP dependency with
 * {@link WireMockTestServer} instead of (or in addition to) a Testcontainers-backed service.
 *
 * <p>Mirrors the {@code @DynamicPropertySource} approach used by {@link BaseIntegrationTest}
 * and the other {@code base.*IntegrationTest} classes: the server is started once for the test
 * class in a static {@code @BeforeAll}, its base URL is published as
 * {@code wiremock.server.base-url} for {@code @Value}/property injection, and stubs are reset
 * between tests so state does not leak across test methods.</p>
 *
 * <p>Subclasses that need the stub base URL under a different property key (e.g.
 * {@code external-service.base-url}) can add their own {@code @DynamicPropertySource} method
 * that reads {@link #wireMockServer}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@SpringBootTest
public abstract class WireMockIntegrationTest {

    protected static WireMockTestServer wireMockServer;

    @BeforeAll
    static void startWireMockServer() {
        wireMockServer = WireMockTestServer.start();
    }

    @AfterAll
    static void stopWireMockServer() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @AfterEach
    void resetWireMockStubs() {
        if (wireMockServer != null) {
            wireMockServer.resetAll();
        }
    }

    @DynamicPropertySource
    static void configureWireMockProperties(DynamicPropertyRegistry registry) {
        registry.add("wiremock.server.base-url", () -> wireMockServer.baseUrl());
    }
}
