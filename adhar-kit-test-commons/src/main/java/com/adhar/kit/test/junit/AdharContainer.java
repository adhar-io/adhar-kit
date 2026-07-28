package com.adhar.kit.test.junit;

/**
 * The backing services that {@link AdharIntegrationTest} / {@link AdharKitExtension} can start and
 * wire up automatically. Each constant maps to one of the module's {@code *TestContainer} helpers
 * and a set of Spring properties published for the test context.
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public enum AdharContainer {
    POSTGRES,
    MONGO,
    REDIS,
    KAFKA,
    LOCALSTACK,
    TOXIPROXY,
    DAPR
}
