package com.adhar.kit.test.container;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A {@link GenericContainer}-based helper for running a standalone Dapr sidecar ({@code daprd}) in
 * integration tests, so code that calls the Dapr HTTP/gRPC APIs (state, pub/sub, service invocation)
 * can be exercised without a full Dapr control plane.
 *
 * <p>Follows the singleton style of the other {@code *TestContainer} helpers. The sidecar is run in
 * standalone mode with its API listening on all interfaces; a configurable app-id is supplied. Point
 * the Dapr SDK at {@link #getHttpEndpoint()} / {@link #getGrpcPort()}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class DaprTestContainer {

    private static final Logger log = LoggerFactory.getLogger(DaprTestContainer.class);
    private static final String DAPRD_IMAGE = "daprio/daprd:1.13.5";
    private static final int HTTP_PORT = 3500;
    private static final int GRPC_PORT = 50001;
    private static final String DEFAULT_APP_ID = "test-app";

    private static GenericContainer<?> container;
    private static String appId = DEFAULT_APP_ID;

    /**
     * Set the Dapr app-id used when the sidecar is (re)created. Must be called before the first
     * {@link #getInstance()}/{@link #start()} to take effect.
     */
    public static void setAppId(String newAppId) {
        appId = newAppId;
    }

    /**
     * Get a singleton Dapr sidecar container instance.
     */
    public static GenericContainer<?> getInstance() {
        if (container == null) {
            container = new GenericContainer<>(DockerImageName.parse(DAPRD_IMAGE))
                    .withExposedPorts(HTTP_PORT, GRPC_PORT)
                    .withCommand(
                            "./daprd",
                            "--app-id", appId,
                            "--dapr-http-port", String.valueOf(HTTP_PORT),
                            "--dapr-grpc-port", String.valueOf(GRPC_PORT),
                            "--dapr-listen-addresses", "0.0.0.0")
                    .waitingFor(Wait.forListeningPort())
                    .withReuse(true);
        }
        return container;
    }

    /**
     * Start the Dapr sidecar container.
     */
    public static void start() {
        getInstance().start();
        log.info("Dapr sidecar container started at {}", getHttpEndpoint());
    }

    /**
     * Stop the Dapr sidecar container.
     */
    public static void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
            log.info("Dapr sidecar container stopped");
        }
    }

    /**
     * Get the Dapr HTTP API base URL, e.g. {@code http://localhost:32768}.
     */
    public static String getHttpEndpoint() {
        return "http://" + getInstance().getHost() + ":" + getInstance().getMappedPort(HTTP_PORT);
    }

    /**
     * Get the mapped Dapr gRPC port on the host.
     */
    public static Integer getGrpcPort() {
        return getInstance().getMappedPort(GRPC_PORT);
    }

    /**
     * Get the Dapr host.
     */
    public static String getHost() {
        return getInstance().getHost();
    }
}
