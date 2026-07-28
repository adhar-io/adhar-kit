package com.adhar.kit.test.base;

import com.adhar.kit.test.container.LocalStackTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that need a LocalStack (AWS emulator) container.
 *
 * <p>The container is started through the shared {@link TestContainerRegistry} - joining the shared
 * Testcontainers network and participating in ordered teardown - and its endpoint/credentials are
 * published as {@code aws.*} properties for injection. Subclasses that need specific services
 * enabled can call {@link LocalStackTestContainer#withServices(String...)} before the container is
 * started (e.g. from their own {@code @BeforeAll} that runs first, or by overriding image setup).</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@SpringBootTest
public abstract class LocalStackIntegrationTest {

    @BeforeAll
    static void initializeContainers() {
        TestContainerRegistry.getInstance().registerAndStart("localstack", LocalStackTestContainer.getInstance());
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("aws.endpoint", () -> LocalStackTestContainer.getEndpoint().toString());
        registry.add("aws.region", LocalStackTestContainer::getRegion);
        registry.add("aws.accessKeyId", LocalStackTestContainer::getAccessKey);
        registry.add("aws.secretAccessKey", LocalStackTestContainer::getSecretKey);
    }
}
