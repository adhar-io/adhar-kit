package com.adhar.kit.kubernetes.health;

import com.adhar.kit.kubernetes.config.ConfigMapReloadService;
import com.adhar.kit.kubernetes.config.SecretWatchService;
import com.adhar.kit.kubernetes.lifecycle.GracefulShutdownHandler;
import com.adhar.kit.kubernetes.service.LeaderElectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KubernetesLivenessHealthIndicator}.
 */
class KubernetesLivenessHealthIndicatorTest {

    @Test
    void reportsUpWithRunningDetailWhenRunning() {
        GracefulShutdownHandler handler = mock(GracefulShutdownHandler.class);
        when(handler.isRunning()).thenReturn(true);

        Health health = new KubernetesLivenessHealthIndicator(handler, null, null, null).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("running"));
    }

    @Test
    void staysUpEvenWhileDraining() {
        // Liveness must not fail during graceful shutdown to avoid restart loops.
        GracefulShutdownHandler handler = mock(GracefulShutdownHandler.class);
        when(handler.isRunning()).thenReturn(false);

        Health health = new KubernetesLivenessHealthIndicator(handler, null, null, null).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(false, health.getDetails().get("running"));
    }

    @Test
    void reportsUpWithoutHandler() {
        Health health = new KubernetesLivenessHealthIndicator(null, null, null, null).health();

        assertEquals(Status.UP, health.getStatus());
    }

    @Test
    void includesLeaderAndWatchDetails() {
        LeaderElectionService leader = mock(LeaderElectionService.class);
        when(leader.isLeader()).thenReturn(false);
        when(leader.getLockName()).thenReturn("lock");
        when(leader.getIdentity()).thenReturn("pod-9");
        ConfigMapReloadService cmWatch = mock(ConfigMapReloadService.class);
        when(cmWatch.getActiveWatchCount()).thenReturn(1);
        SecretWatchService secretWatch = mock(SecretWatchService.class);
        when(secretWatch.getActiveWatchCount()).thenReturn(0);

        Health health = new KubernetesLivenessHealthIndicator(null, leader, cmWatch, secretWatch).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("follower", health.getDetails().get("leaderElection"));
        assertEquals(1, health.getDetails().get("configMapWatches"));
        assertEquals(0, health.getDetails().get("secretWatches"));
        assertTrue(health.getDetails().containsKey("leaderIdentity"));
    }
}
