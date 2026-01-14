package com.adhar.kit.grpc;

import io.grpc.Server;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Lifecycle management for gRPC Server.
 *
 * <p>This class manages the lifecycle of a gRPC server within the Spring context,
 * ensuring proper startup and shutdown of the server.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class GrpcServerLifecycle implements SmartLifecycle {

    private final Server server;
    private volatile boolean running = false;

    @Override
    public void start() {
        if (!running) {
            try {
                server.start();
                running = true;
                log.info("gRPC Server started on port {}", server.getPort());

                // Add shutdown hook
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    log.info("Shutting down gRPC server...");
                    GrpcServerLifecycle.this.stop();
                }));
            } catch (IOException e) {
                log.error("Failed to start gRPC server", e);
                throw new RuntimeException("Failed to start gRPC server", e);
            }
        }
    }

    @Override
    public void stop() {
        if (running) {
            try {
                server.shutdown().awaitTermination(30, TimeUnit.SECONDS);
                running = false;
                log.info("gRPC Server stopped");
            } catch (InterruptedException e) {
                log.error("Error during gRPC server shutdown", e);
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        callback.run();
    }

    /**
     * Gets the underlying gRPC server.
     *
     * @return the gRPC server
     */
    public Server getServer() {
        return server;
    }

    /**
     * Gets the port the server is listening on.
     *
     * @return the server port
     */
    public int getPort() {
        return server.getPort();
    }
}

