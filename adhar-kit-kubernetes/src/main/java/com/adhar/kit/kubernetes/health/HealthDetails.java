package com.adhar.kit.kubernetes.health;

import com.adhar.kit.kubernetes.config.ConfigMapReloadService;
import com.adhar.kit.kubernetes.config.SecretWatchService;
import com.adhar.kit.kubernetes.service.LeaderElectionService;
import org.springframework.boot.health.contributor.Health;

/**
 * Shared helper that attaches leader-election and watch-subsystem details to a
 * {@link Health.Builder}. Kept in one place so the readiness and liveness
 * indicators report identical subsystem details.
 */
final class HealthDetails {

    private HealthDetails() {
    }

    /**
     * Adds leader-election role and active watch counts as {@link Health} details
     * for whichever subsystems are present (non-{@code null}).
     *
     * @param builder        the health builder to enrich
     * @param leaderElection leader election service (nullable)
     * @param configMapWatch ConfigMap watch service (nullable)
     * @param secretWatch    Secret watch service (nullable)
     */
    static void contribute(Health.Builder builder,
                           LeaderElectionService leaderElection,
                           ConfigMapReloadService configMapWatch,
                           SecretWatchService secretWatch) {
        if (leaderElection != null) {
            builder.withDetail("leaderElection", leaderElection.isLeader() ? "leader" : "follower");
            builder.withDetail("leaderLock", leaderElection.getLockName());
            builder.withDetail("leaderIdentity", leaderElection.getIdentity());
        }
        if (configMapWatch != null) {
            builder.withDetail("configMapWatches", configMapWatch.getActiveWatchCount());
        }
        if (secretWatch != null) {
            builder.withDetail("secretWatches", secretWatch.getActiveWatchCount());
        }
    }
}
