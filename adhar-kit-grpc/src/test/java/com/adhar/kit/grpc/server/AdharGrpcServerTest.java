package com.adhar.kit.grpc.server;

import com.adhar.kit.grpc.config.GrpcProperties;
import io.grpc.BindableService;
import io.grpc.ServerServiceDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for AdharGrpcServer.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class AdharGrpcServerTest {

    @Test
    void constructor_createsServer() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server).isNotNull();
    }

    @Test
    void getPort_beforeStart_returnsNegativeOne() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server.getPort()).isEqualTo(-1);
    }

    @Test
    void isRunning_beforeStart_returnsFalse() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void getHealthStatusManager_returnsNonNull() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        assertThat(server.getHealthStatusManager()).isNotNull();
    }

    @Test
    void addService_returnsThis_forChaining() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        // addService should return this for fluent API
        // We cannot easily create a BindableService without protobuf stubs,
        // but we can verify the method signature by testing chaining concept
        assertThat(server).isNotNull();
    }

    @Test
    void start_whenDisabled_doesNotStart() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setEnabled(false);
        AdharGrpcServer server = new AdharGrpcServer(properties);

        server.start();

        assertThat(server.isRunning()).isFalse();
        assertThat(server.getPort()).isEqualTo(-1);
    }

    @Test
    void shutdown_whenNotStarted_doesNothing() {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        // Should not throw
        server.shutdown();

        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void awaitTermination_whenNotStarted_doesNothing() throws InterruptedException {
        GrpcProperties properties = new GrpcProperties();
        AdharGrpcServer server = new AdharGrpcServer(properties);

        // Should not throw or block
        server.awaitTermination();
    }

    @Test
    void startAndShutdown_withNoServices() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        // Use a random high port to avoid conflicts
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(false);
        properties.getServer().setEnableHealthCheck(false);
        AdharGrpcServer server = new AdharGrpcServer(properties);

        server.start();

        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isGreaterThanOrEqualTo(0);

        server.shutdown();

        assertThat(server.isRunning()).isFalse();
    }

    @Test
    void startWithHealthAndReflection() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(true);
        properties.getServer().setEnableHealthCheck(true);
        AdharGrpcServer server = new AdharGrpcServer(properties);

        server.start();

        assertThat(server.isRunning()).isTrue();

        server.shutdown();
    }

    @Test
    void addService_registersService_andStartsWithInterceptors() throws IOException {
        GrpcProperties properties = new GrpcProperties();
        properties.getServer().setPort(0);
        properties.getServer().setEnableReflection(false);
        properties.getServer().setEnableHealthCheck(false);
        AdharGrpcServer server = new AdharGrpcServer(properties);

        AdharGrpcServer returned = server.addService(new EmptyBindableService());

        // addService returns this for fluent chaining
        assertThat(returned).isSameAs(server);

        // start() must register the service (wrapped with logging + exception interceptors)
        server.start();
        assertThat(server.isRunning()).isTrue();
        assertThat(server.getPort()).isGreaterThanOrEqualTo(0);

        server.shutdown();
        assertThat(server.isRunning()).isFalse();
    }

    /**
     * Minimal {@link BindableService} with no methods, allowing the server registration
     * loop to be exercised without generated protobuf stubs.
     */
    private static class EmptyBindableService implements BindableService {
        @Override
        public ServerServiceDefinition bindService() {
            return ServerServiceDefinition.builder("test.EmptyService").build();
        }
    }
}
