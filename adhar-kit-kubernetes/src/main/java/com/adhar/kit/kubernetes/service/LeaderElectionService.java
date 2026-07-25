package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.util.KubernetesUtils;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderCallbacks;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfig;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectionConfigBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElector;
import io.fabric8.kubernetes.client.extended.leaderelection.LeaderElectorBuilder;
import io.fabric8.kubernetes.client.extended.leaderelection.resourcelock.LeaseLock;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Backs the {@link com.adhar.kit.kubernetes.annotation.LeaderElected} annotation using
 * Fabric8's {@link LeaderElector} / Kubernetes {@code Lease} API.
 *
 * <p>Only one instance across the cluster will ever be leader for a given lock name.
 * Callbacks registered via {@link #onStartedLeading(Runnable)} and
 * {@link #onStoppedLeading(Runnable)} are invoked as leadership transitions occur.</p>
 *
 * <p><b>Graceful degradation:</b> when not running inside a cluster (or when the
 * Fabric8 client cannot be constructed), this service never throws. {@link #start()}
 * logs a warning and leaves {@link #isLeader()} permanently {@code false} instead of
 * failing application startup.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class LeaderElectionService {

    private final String lockName;
    private final String namespace;
    private final Duration leaseDuration;
    private final Duration renewDeadline;
    private final Duration retryPeriod;
    private final String identity;

    private final io.fabric8.kubernetes.client.KubernetesClient client;

    private final List<Runnable> startedLeadingCallbacks = new CopyOnWriteArrayList<>();
    private final List<Runnable> stoppedLeadingCallbacks = new CopyOnWriteArrayList<>();

    private volatile boolean leader = false;
    private volatile ExecutorService executor;
    private volatile LeaderElector elector;
    private volatile CompletableFuture<?> future;

    /**
     * Creates a leader election service for the given lock.
     *
     * @param lockName      name of the Lease/lock used to coordinate leadership
     * @param namespace     namespace the lock lives in (falls back to the pod's namespace when blank)
     * @param leaseDuration duration a leader's term lasts before it must be renewed
     * @param renewDeadline deadline within which the leader must renew its lease
     * @param retryPeriod   how often non-leaders retry to acquire leadership
     */
    public LeaderElectionService(String lockName, String namespace, Duration leaseDuration,
                                  Duration renewDeadline, Duration retryPeriod) {
        this.lockName = lockName;
        this.namespace = (namespace == null || namespace.isBlank()) ? KubernetesUtils.getNamespace() : namespace;
        this.leaseDuration = leaseDuration;
        this.renewDeadline = renewDeadline;
        this.retryPeriod = retryPeriod;
        this.identity = resolveIdentity();
        this.client = createFabric8Client();
    }

    private static String resolveIdentity() {
        String pod = KubernetesUtils.getPodName();
        return pod != null ? pod : UUID.randomUUID().toString();
    }

    private io.fabric8.kubernetes.client.KubernetesClient createFabric8Client() {
        try {
            return new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
        } catch (Throwable e) {
            // Catches both runtime exceptions and linkage/config errors thrown when a
            // Fabric8 HTTP client cannot be built outside a cluster/dev environment.
            log.warn("Failed to create Kubernetes client for leader election lock '{}' - " +
                    "leader election will be disabled for this instance", lockName, e);
            return null;
        }
    }

    /**
     * Registers a callback invoked when this instance becomes leader.
     * If this instance is already leader, the callback fires immediately.
     *
     * @param callback callback to invoke
     */
    public void onStartedLeading(Runnable callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        startedLeadingCallbacks.add(callback);
        if (leader) {
            safeRun(callback);
        }
    }

    /**
     * Registers a callback invoked when this instance stops being leader (or never
     * becomes leader, e.g. on shutdown).
     *
     * @param callback callback to invoke
     */
    public void onStoppedLeading(Runnable callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        stoppedLeadingCallbacks.add(callback);
    }

    /**
     * @return true if this instance currently holds leadership
     */
    public boolean isLeader() {
        return leader;
    }

    /**
     * @return the identity this instance uses to participate in leader election
     */
    public String getIdentity() {
        return identity;
    }

    /**
     * @return the lock name used to coordinate leadership
     */
    public String getLockName() {
        return lockName;
    }

    /**
     * Starts participating in leader election. Safe to call even when not running
     * in a cluster - degrades to a no-op (never becomes leader).
     */
    public synchronized void start() {
        if (client == null) {
            log.warn("Kubernetes client unavailable; leader election for lock '{}' will not run", lockName);
            return;
        }
        if (elector != null) {
            return;
        }
        try {
            LeaseLock lock = new LeaseLock(namespace, lockName, identity);
            LeaderElectionConfig config = new LeaderElectionConfigBuilder()
                    .withName(lockName)
                    .withLock(lock)
                    .withLeaseDuration(leaseDuration)
                    .withRenewDeadline(renewDeadline)
                    .withRetryPeriod(retryPeriod)
                    .withReleaseOnCancel(true)
                    .withLeaderCallbacks(new LeaderCallbacks(
                            this::handleStartLeading,
                            this::handleStopLeading,
                            newLeader -> log.info("New leader observed for lock '{}': {}", lockName, newLeader)))
                    .build();

            executor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "leader-election-" + lockName);
                t.setDaemon(true);
                return t;
            });

            elector = buildElector(config, executor);
            future = elector.start();
            log.info("Started leader election for lock '{}' in namespace '{}' as identity '{}'",
                    lockName, namespace, identity);
        } catch (Exception e) {
            log.warn("Failed to start leader election for lock '{}' - degrading gracefully", lockName, e);
            elector = null;
        }
    }

    /**
     * Builds the underlying {@link LeaderElector}. Package-visible so tests can
     * substitute a fake elector without talking to a real API server.
     */
    LeaderElector buildElector(LeaderElectionConfig config, ExecutorService electorExecutor) {
        return new LeaderElectorBuilder(client, electorExecutor).withConfig(config).build();
    }

    /**
     * Stops participating in leader election, releasing the lock if held.
     */
    public synchronized void stop() {
        try {
            if (elector != null) {
                elector.release();
            }
        } catch (Exception e) {
            log.debug("Error releasing leader election lock '{}'", lockName, e);
        } finally {
            if (future != null) {
                future.cancel(true);
                future = null;
            }
            if (executor != null) {
                executor.shutdownNow();
                executor = null;
            }
            elector = null;
            handleStopLeading();
        }
    }

    /**
     * Invoked when this instance acquires leadership. Public so the underlying
     * elector's callbacks (and tests) can trigger it directly.
     */
    public void handleStartLeading() {
        leader = true;
        log.info("Acquired leadership for lock '{}'", lockName);
        startedLeadingCallbacks.forEach(this::safeRun);
    }

    /**
     * Invoked when this instance loses (or never acquires) leadership. Public so the
     * underlying elector's callbacks (and tests) can trigger it directly.
     */
    public void handleStopLeading() {
        boolean wasLeader = leader;
        leader = false;
        if (wasLeader) {
            log.info("Lost leadership for lock '{}'", lockName);
        }
        stoppedLeadingCallbacks.forEach(this::safeRun);
    }

    private void safeRun(Runnable callback) {
        try {
            callback.run();
        } catch (Exception e) {
            log.error("Leader election callback failed for lock '{}'", lockName, e);
        }
    }
}
