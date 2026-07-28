package com.adhar.kit.kubernetes.health;

import com.adhar.kit.kubernetes.config.ConfigMapReloadService;
import com.adhar.kit.kubernetes.config.SecretWatchService;
import com.adhar.kit.kubernetes.lifecycle.GracefulShutdownHandler;
import com.adhar.kit.kubernetes.service.LeaderElectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/**
 * Liveness {@link HealthIndicator} for the Kubernetes integration.
 *
 * <p>Liveness answers "is this process alive and should it keep running?". It
 * deliberately reports {@code UP} whenever the application context is up -
 * including while the pod is draining during graceful shutdown - so that a
 * transient loss of API-server connectivity or an in-progress shutdown never
 * triggers a liveness-probe restart loop (a Kubernetes anti-pattern). The
 * lifecycle running flag, leader-election role and active watch counts are
 * attached as informational details. Wire this indicator into the liveness probe
 * group (e.g. {@code management.endpoint.health.group.liveness.include}).</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
public class KubernetesLivenessHealthIndicator implements HealthIndicator {

    private final GracefulShutdownHandler shutdownHandler;
    private final LeaderElectionService leaderElection;
    private final ConfigMapReloadService configMapWatch;
    private final SecretWatchService secretWatch;

    /**
     * @param shutdownHandler graceful-shutdown handler (nullable)
     * @param leaderElection  leader election service (nullable)
     * @param configMapWatch  ConfigMap watch service (nullable)
     * @param secretWatch     Secret watch service (nullable)
     */
    public KubernetesLivenessHealthIndicator(GracefulShutdownHandler shutdownHandler,
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
        Health.Builder builder = Health.up();
        if (shutdownHandler != null) {
            builder.withDetail("running", shutdownHandler.isRunning());
        }
        HealthDetails.contribute(builder, leaderElection, configMapWatch, secretWatch);
        return builder.build();
    }
}
