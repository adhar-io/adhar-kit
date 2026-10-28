package com.adhar.kit.dapr.health;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.config.DaprProperties;
import com.adhar.kit.dapr.config.DaprSidecarLifecycle;
import com.adhar.kit.dapr.support.FakeSidecar;
import com.adhar.kit.dapr.support.MemoryDapr;
import io.dapr.client.DaprClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The sidecar health indicator and the bounded startup wait.
 */
class DaprHealthAndLifecycleTest {

    private static DaprFacade sidecarFacade(FakeSidecar sidecar) {
        return DaprFacade.builder().connection(sidecar.connection("app", null)).client(mock(DaprClient.class)).build();
    }

    @Test
    void healthFollowsTheSidecar() {
        try (FakeSidecar sidecar = new FakeSidecar()) {
            sidecar.respond("/v1.0/healthz/outbound", 204, "");
            DaprFacade dapr = sidecarFacade(sidecar);

            Health up = new DaprHealthIndicator(dapr).health();
            assertThat(up.getStatus()).isEqualTo(Status.UP);
            assertThat(up.getDetails()).containsEntry("mode", "sidecar").containsEntry("appId", "app");

            sidecar.close();
            assertThat(new DaprHealthIndicator(dapr, Duration.ofMillis(300)).health().getStatus()).isEqualTo(Status.DOWN);
        }
    }

    @Test
    void memoryModeIsAlwaysUp() {
        Health health = new DaprHealthIndicator(MemoryDapr.facade()).health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("mode", "memory").containsEntry("endpoint", "in-process");
    }

    @Test
    void startupWaitsForTheSidecarAndIsBounded() {
        try (FakeSidecar sidecar = new FakeSidecar()) {
            sidecar.respond("/v1.0/healthz/outbound", 204, "");
            DaprFacade dapr = sidecarFacade(sidecar);
            DaprProperties.SidecarConfig config = new DaprProperties.SidecarConfig();
            config.setWaitTimeout(Duration.ofSeconds(2));

            DaprSidecarLifecycle lifecycle = new DaprSidecarLifecycle(dapr, config);
            lifecycle.start();
            assertThat(dapr.isAvailable()).isTrue();
            assertThat(lifecycle.isRunning()).isTrue();
            assertThat(lifecycle.getPhase()).isLessThan(org.springframework.context.SmartLifecycle.DEFAULT_PHASE - 1024);
            lifecycle.stop();

            sidecar.close();
            DaprFacade unreachable = sidecarFacade(sidecar);
            config.setWaitTimeout(Duration.ofMillis(300));
            new DaprSidecarLifecycle(unreachable, config).start();
            assertThat(unreachable.isAvailable()).isFalse();

            config.setFailOnTimeout(true);
            assertThatThrownBy(() -> new DaprSidecarLifecycle(unreachable, config).start())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("was not ready after");
        }
    }

    @Test
    void memoryModeNeverWaits() {
        DaprProperties.SidecarConfig config = new DaprProperties.SidecarConfig();
        config.setFailOnTimeout(true);
        new DaprSidecarLifecycle(MemoryDapr.facade(), config).start();
    }
}
