package com.adhar.kit.grpc;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for GrpcServerLifecycle.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
class GrpcServerLifecycleTest {

    @Test
    void isRunning_initiallyFalse() {
        Server server = ServerBuilder.forPort(0).build();
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);

        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void getServer_returnsProvidedServer() {
        Server server = ServerBuilder.forPort(0).build();
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);

        assertThat(lifecycle.getServer()).isSameAs(server);
    }

    @Test
    void getPhase_returnsMaxValue() {
        Server server = ServerBuilder.forPort(0).build();
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);

        assertThat(lifecycle.getPhase()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void isAutoStartup_returnsTrue() {
        Server server = ServerBuilder.forPort(0).build();
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);

        assertThat(lifecycle.isAutoStartup()).isTrue();
    }

    @Test
    void startAndStop_lifecycle() throws IOException {
        Server server = ServerBuilder.forPort(0).build();
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);

        lifecycle.start();
        assertThat(lifecycle.isRunning()).isTrue();
        assertThat(lifecycle.getPort()).isGreaterThanOrEqualTo(0);

        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void stop_whenNotRunning_doesNothing() {
        Server server = ServerBuilder.forPort(0).build();
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);

        // Should not throw
        lifecycle.stop();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void stopWithCallback_invokesCallback() throws IOException {
        Server server = ServerBuilder.forPort(0).build();
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);
        lifecycle.start();

        boolean[] callbackInvoked = {false};
        lifecycle.stop(() -> callbackInvoked[0] = true);

        assertThat(callbackInvoked[0]).isTrue();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void start_whenServerFailsToStart_throwsRuntimeException() throws IOException {
        Server server = mock(Server.class);
        when(server.start()).thenThrow(new IOException("bind failed"));
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);

        assertThatThrownBy(lifecycle::start)
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to start gRPC server");
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void startTwice_onlyStartsOnce() throws IOException {
        Server server = ServerBuilder.forPort(0).build();
        GrpcServerLifecycle lifecycle = new GrpcServerLifecycle(server);

        lifecycle.start();
        int portAfterFirstStart = lifecycle.getPort();

        // Starting again should be a no-op
        lifecycle.start();
        assertThat(lifecycle.getPort()).isEqualTo(portAfterFirstStart);

        lifecycle.stop();
    }
}
