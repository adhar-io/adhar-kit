package com.adhar.kit.test.assertion;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.api.AbstractAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Custom assertions for common test scenarios.
 * Extends AssertJ with domain-specific assertions.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class CustomAssertions {

    /**
     * Assert that two LocalDateTime objects are approximately equal (within seconds).
     */
    public static void assertTimeApproximately(LocalDateTime actual, LocalDateTime expected, long seconds) {
        long diff = Math.abs(ChronoUnit.SECONDS.between(actual, expected));
        if (diff > seconds) {
            throw new AssertionError(
                String.format("Expected time to be within %d seconds of %s but was %s (diff: %d seconds)",
                    seconds, expected, actual, diff)
            );
        }
    }

    /**
     * Assert that a timestamp is recent (within last minute).
     */
    public static void assertRecentTimestamp(LocalDateTime timestamp) {
        assertTimeApproximately(timestamp, LocalDateTime.now(), 60);
    }

    /**
     * Assert that a string is a valid UUID.
     */
    public static void assertValidUUID(String value) {
        if (value == null || !value.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            throw new AssertionError(
                String.format("Expected valid UUID but was: %s", value)
            );
        }
    }

    /**
     * Assert that a string is a valid email.
     */
    public static void assertValidEmail(String email) {
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
            throw new AssertionError(
                String.format("Expected valid email but was: %s", email)
            );
        }
    }

    /**
     * Assert that a collection is not empty.
     */
    public static void assertNotEmpty(java.util.Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            throw new AssertionError("Expected non-empty collection but was empty or null");
        }
    }

    /**
     * Assert that two objects have the same property values (excluding specified fields).
     */
    public static void assertSameProperties(Object actual, Object expected, String... excludeFields) {
        org.assertj.core.api.Assertions.assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields(excludeFields)
            .isEqualTo(expected);
    }
}

/**
 * Mock REST server utilities using WireMock.
 */
class MockRestServer {

    private static final Logger log = LoggerFactory.getLogger(MockRestServer.class);
    private static WireMockServer wireMockServer;

    /**
     * Start WireMock server on a random port.
     */
    public static void start() {
        start(0); // Random port
    }

    /**
     * Start WireMock server on a specific port.
     */
    public static void start(int port) {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            WireMockConfiguration config = WireMockConfiguration.options();

            if (port > 0) {
                config.port(port);
            } else {
                config.dynamicPort();
            }

            wireMockServer = new WireMockServer(config);
            wireMockServer.start();

            WireMock.configureFor(wireMockServer.port());

            log.info("WireMock server started on port: {}", wireMockServer.port());
        }
    }

    /**
     * Stop WireMock server.
     */
    public static void stop() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            log.info("WireMock server stopped");
        }
    }

    /**
     * Reset all WireMock stubs.
     */
    public static void reset() {
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }

    /**
     * Get the port WireMock is running on.
     */
    public static int getPort() {
        if (wireMockServer == null || !wireMockServer.isRunning()) {
            throw new IllegalStateException("WireMock server is not running");
        }
        return wireMockServer.port();
    }

    /**
     * Get the base URL of the WireMock server.
     */
    public static String getBaseUrl() {
        return String.format("http://localhost:%d", getPort());
    }

    /**
     * Get the WireMock server instance.
     */
    public static WireMockServer getInstance() {
        return wireMockServer;
    }
}

