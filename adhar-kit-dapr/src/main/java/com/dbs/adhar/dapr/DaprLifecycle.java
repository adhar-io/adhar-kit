package com.dbs.adhar.dapr;

import io.dapr.client.DaprClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;

/**
 * Manages the lifecycle of the Dapr client.
 */
public class DaprLifecycle implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(DaprLifecycle.class);

    @Autowired
    private DaprClient daprClient;

    private volatile boolean running;

    @Override
    public void start() {
        logger.info("Starting Dapr client");
        daprClient.waitForSidecar(60000);
        this.running = true;
        logger.info("Dapr client started");
    }

    @Override
    public void stop() {
        logger.info("Stopping Dapr client");
        daprClient.close();
        this.running = false;
        logger.info("Dapr client stopped");
    }

    @Override
    public boolean isRunning() {
        return this.running;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }\n
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

