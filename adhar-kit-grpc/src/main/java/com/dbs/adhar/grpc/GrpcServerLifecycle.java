package com.dbs.adhar.grpc;

import io.grpc.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;

public class GrpcServerLifecycle implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(GrpcServerLifecycle.class);

    private final Server server;
    private volatile boolean running;

    public GrpcServerLifecycle(Server server) {
        this.server = server;
    }

    @Override
    public void start() {
        try {
            server.start();
            logger.info("gRPC server started on port {}", server.getPort());
            this.running = true;
        } catch (IOException e) {
            logger.error("Failed to start gRPC server", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.shutdown();
            this.running = false;
            logger.info("gRPC server stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return this.running;
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
}

