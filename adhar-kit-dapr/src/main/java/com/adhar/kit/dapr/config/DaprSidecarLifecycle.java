package com.adhar.kit.dapr.config;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.Objects;

/**
 * Waits for the sidecar ({@code GET /v1.0/healthz/outbound}) while the application starts, before
 * the web server opens, so the first request does not race the sidecar. The wait is bounded and,
 * unless {@code adhar.dapr.sidecar.fail-on-timeout} is set, a sidecar that never comes is logged
 * as an error rather than failing startup. Nothing is waited for in memory mode.
 *
 * <p>The outbound health endpoint does not depend on the app being up, so waiting here cannot
 * deadlock with a sidecar that is itself waiting for the app's port.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class DaprSidecarLifecycle implements SmartLifecycle {

    /** Before the web server (which starts at {@code DEFAULT_PHASE - 1024}). */
    public static final int PHASE = SmartLifecycle.DEFAULT_PHASE - 2048;

    private final DaprFacade dapr;
    private final DaprProperties.SidecarConfig config;
    private volatile boolean running;

    public DaprSidecarLifecycle(DaprFacade dapr, DaprProperties.SidecarConfig config) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.config = config != null ? config : new DaprProperties.SidecarConfig();
    }

    @Override
    public void start() {
        running = true;
        if (dapr.mode() == DaprMode.MEMORY || !config.isWaitOnStartup()) {
            return;
        }
        Duration timeout = config.getWaitTimeout() != null ? config.getWaitTimeout() : Duration.ofSeconds(30);
        log.info("Waiting up to {} for the Dapr sidecar at {}", timeout, dapr.connection().httpEndpoint());
        if (!dapr.waitForSidecar(timeout)) {
            String message = "Dapr sidecar at " + dapr.connection().httpEndpoint() + " was not ready after " + timeout;
            if (config.isFailOnTimeout()) {
                throw new IllegalStateException(message);
            }
            log.error("{}; continuing, Dapr calls fail until it is (adhar.dapr.sidecar.fail-on-timeout=true makes "
                    + "this fatal)", message);
        }
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
