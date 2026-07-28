package com.adhar.kit.kubernetes.health;

import com.adhar.kit.kubernetes.config.ConfigMapReloadService;
import com.adhar.kit.kubernetes.config.SecretWatchService;
import com.adhar.kit.kubernetes.lifecycle.GracefulShutdownHandler;
import com.adhar.kit.kubernetes.service.LeaderElectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link KubernetesReadinessHealthIndicator}.
 */
class KubernetesReadinessHealthIndicatorTest {

    @Test
    void reportsUpWhenReady() {
        GracefulShutdownHandler handler = mock(GracefulShutdownHandler.class);
        when(handler.isReady()).thenReturn(true);

        Health health = new KubernetesReadinessHealthIndicator(handler, null, null, null).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("ready"));
        assertEquals(false, health.getDetails().get("draining"));
    }

    @Test
    void reportsOutOfServiceWhenDraining() {
        GracefulShutdownHandler handler = mock(GracefulShutdownHandler.class);
        when(handler.isReady()).thenReturn(false);

        Health health = new KubernetesReadinessHealthIndicator(handler, null, null, null).health();

        assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
        assertEquals(false, health.getDetails().get("ready"));
        assertEquals(true, health.getDetails().get("draining"));
    }

    @Test
    void treatsMissingHandlerAsReady() {
        Health health = new KubernetesReadinessHealthIndicator(null, null, null, null).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals(true, health.getDetails().get("ready"));
        // No handler -> no draining detail is added.
        assertFalse(health.getDetails().containsKey("draining"));
    }

    @Test
    void includesLeaderElectionAndWatchDetails() {
        GracefulShutdownHandler handler = mock(GracefulShutdownHandler.class);
        when(handler.isReady()).thenReturn(true);
        LeaderElectionService leader = mock(LeaderElectionService.class);
        when(leader.isLeader()).thenReturn(true);
        when(leader.getLockName()).thenReturn("my-lock");
        when(leader.getIdentity()).thenReturn("pod-1");
        ConfigMapReloadService cmWatch = mock(ConfigMapReloadService.class);
        when(cmWatch.getActiveWatchCount()).thenReturn(2);
        SecretWatchService secretWatch = mock(SecretWatchService.class);
        when(secretWatch.getActiveWatchCount()).thenReturn(3);

        Health health = new KubernetesReadinessHealthIndicator(handler, leader, cmWatch, secretWatch).health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("leader", health.getDetails().get("leaderElection"));
        assertEquals("my-lock", health.getDetails().get("leaderLock"));
        assertEquals("pod-1", health.getDetails().get("leaderIdentity"));
        assertEquals(2, health.getDetails().get("configMapWatches"));
        assertEquals(3, health.getDetails().get("secretWatches"));
    }

    @Test
    void reportsFollowerRoleWhenNotLeader() {
        LeaderElectionService leader = mock(LeaderElectionService.class);
        when(leader.isLeader()).thenReturn(false);
        when(leader.getLockName()).thenReturn("lock");
        when(leader.getIdentity()).thenReturn("pod-2");

        Health health = new KubernetesReadinessHealthIndicator(null, leader, null, null).health();

        assertEquals("follower", health.getDetails().get("leaderElection"));
        assertTrue(health.getDetails().containsKey("leaderIdentity"));
    }
}
