package com.adhar.kit.kubernetes.health;

import com.adhar.kit.kubernetes.config.ConfigMapReloadService;
import com.adhar.kit.kubernetes.config.SecretWatchService;
import com.adhar.kit.kubernetes.lifecycle.GracefulShutdownHandler;
import com.adhar.kit.kubernetes.service.LeaderElectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Readiness {@link HealthIndicator} that surfaces the pod's serving readiness.
 *
 * <p>Reports {@code OUT_OF_SERVICE} once {@link GracefulShutdownHandler} has begun
 * draining (readiness flag flipped to {@code false} on SIGTERM/context close), and
 * {@code UP} while the pod is serving. Wire this indicator into the readiness probe
 * group (e.g. {@code management.endpoint.health.group.readiness.include}) so that
 * kube-proxy removes the pod from Service endpoints during graceful shutdown.</p>
 *
 * <p>Leader-election role and active config/secret watch counts are attached as
 * informational details; they do not by themselves fail readiness. When no
 * {@link GracefulShutdownHandler} is present the pod is treated as ready.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class KubernetesReadinessHealthIndicator implements HealthIndicator {

    private final GracefulShutdownHandler shutdownHandler;
    private final LeaderElectionService leaderElection;
    private final ConfigMapReloadService configMapWatch;
    private final SecretWatchService secretWatch;

    /**
     * @param shutdownHandler graceful-shutdown handler exposing the readiness flag (nullable)
     * @param leaderElection  leader election service (nullable)
     * @param configMapWatch  ConfigMap watch service (nullable)
     * @param secretWatch     Secret watch service (nullable)
     */
    public KubernetesReadinessHealthIndicator(GracefulShutdownHandler shutdownHandler,
                                              LeaderElectionService leaderElection,
                                              ConfigMapReloadService configMapWatch,
                                              SecretWatchService secretWatch) {
        this.shutdownHandler = shutdownHandler;
        this.leaderElection = leaderElection;
        this.configMapWatch = configMapWatch;
        this.secretWatch = secretWatch;
    }

    @Override
    public Health health() {
        boolean ready = shutdownHandler == null || shutdownHandler.isReady();
        Health.Builder builder = ready ? Health.up() : Health.outOfService();
        builder.withDetail("ready", ready);
        if (shutdownHandler != null) {
            builder.withDetail("draining", !ready);
        }
        HealthDetails.contribute(builder, leaderElection, configMapWatch, secretWatch);
        return builder.build();
    }
}
