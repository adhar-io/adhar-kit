package com.adhar.kit.kubernetes.lifecycle;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Coordinates graceful shutdown with a Kubernetes readiness probe: on shutdown
 * (context close / SIGTERM), immediately flips a readiness flag to {@code false} so a
 * readiness probe starts failing, then sleeps a configurable pre-stop drain period
 * before allowing the JVM to actually terminate - giving load balancers/kube-proxy time
 * to remove this pod from Service endpoints before in-flight requests are cut off.
 *
 * <p>Wire {@link #isReady()} into a readiness health indicator/probe endpoint.</p>
 *
 * <p>Runs at the latest possible {@link SmartLifecycle} phase so it starts last and
 * stops <em>first</em> among {@link SmartLifecycle} beans, maximizing the drain window
 * before other infrastructure (e.g. leader election) tears down.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class GracefulShutdownHandler implements SmartLifecycle {

    /**
     * Sleep function, package-visible so tests can avoid real sleeping.
     */
    @FunctionalInterface
    interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final Duration drainPeriod;
    private final Sleeper sleeper;
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * @param drainPeriod how long to sleep after readiness flips false and before
     *                    shutdown proceeds; {@code null} or non-positive disables draining
     */
    public GracefulShutdownHandler(Duration drainPeriod) {
        this(drainPeriod, Thread::sleep);
    }

    GracefulShutdownHandler(Duration drainPeriod, Sleeper sleeper) {
        this.drainPeriod = drainPeriod == null ? Duration.ZERO : drainPeriod;
        this.sleeper = sleeper;
    }

    @Override
    public void start() {
        ready.set(true);
        running.set(true);
        log.info("Readiness gate open");
    }

    @Override
    public void stop() {
        stop(() -> {
        });
    }

    @Override
    public void stop(Runnable callback) {
        try {
            beginShutdown();
        } finally {
            callback.run();
        }
    }

    /**
     * Flips readiness to false and drains, if not already shutting down. Also invoked
     * via {@code @PreDestroy} to cover non-SmartLifecycle-managed shutdown paths (e.g.
     * plain {@code ConfigurableApplicationContext.close()} ordering edge cases, or usage
     * of this class outside of Spring's lifecycle machinery).
     */
    @PreDestroy
    public void beginShutdown() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ready.set(false);
        log.info("Graceful shutdown initiated - readiness=false, draining for {}", drainPeriod);
        if (!drainPeriod.isZero() && !drainPeriod.isNegative()) {
            try {
                sleeper.sleep(drainPeriod.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Graceful shutdown drain sleep interrupted", e);
            }
        }
        log.info("Graceful shutdown drain period complete");
    }

    /**
     * @return true once {@link #start()} has run and until shutdown begins; wire this
     * into a readiness probe/health indicator
     */
    public boolean isReady() {
        return ready.get();
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
