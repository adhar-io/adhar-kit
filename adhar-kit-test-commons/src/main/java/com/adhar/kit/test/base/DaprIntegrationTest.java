package com.adhar.kit.test.base;

import com.adhar.kit.test.container.DaprTestContainer;
import com.adhar.kit.test.container.TestContainerRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that need a standalone Dapr sidecar container.
 *
 * <p>The sidecar is started through the shared {@link TestContainerRegistry} and its HTTP/gRPC
 * endpoints are published as {@code dapr.*} properties for injection, mirroring the other
 * {@code base.*IntegrationTest} classes.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@SpringBootTest
public abstract class DaprIntegrationTest {

    @BeforeAll
    static void initializeContainers() {
        TestContainerRegistry.getInstance().registerAndStart("dapr", DaprTestContainer.getInstance());
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("dapr.http.endpoint", DaprTestContainer::getHttpEndpoint);
        registry.add("dapr.grpc.port", DaprTestContainer::getGrpcPort);
    }
}
