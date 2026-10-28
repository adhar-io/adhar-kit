package com.adhar.kit.dapr.health;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.DaprMode;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import java.time.Duration;
import java.util.Objects;

/**
 * Reports whether the Dapr sidecar is ready ({@code GET /v1.0/healthz/outbound}). Always UP in
 * memory mode (there is nothing to be down), with {@code mode: memory} so a dashboard can tell.
 *
 * <p>Contributed as {@code dapr}; add it to the readiness group
 * ({@code management.endpoint.health.group.readiness.include=readinessState,dapr}) to keep traffic
 * away from a pod whose sidecar is gone.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprHealthIndicator implements HealthIndicator {

    private final DaprFacade dapr;
    private final Duration timeout;

    public DaprHealthIndicator(DaprFacade dapr) {
        this(dapr, Duration.ofSeconds(2));
    }

    public DaprHealthIndicator(DaprFacade dapr, Duration timeout) {
        this.dapr = Objects.requireNonNull(dapr, "dapr must not be null");
        this.timeout = timeout;
    }

    @Override
    public Health health() {
        Health.Builder builder = dapr.mode() == DaprMode.MEMORY || dapr.checkSidecar(timeout)
                ? Health.up() : Health.down();
        return builder
                .withDetail("mode", dapr.mode().name().toLowerCase(java.util.Locale.ROOT))
                .withDetail("appId", dapr.connection().appId())
                .withDetail("endpoint", dapr.mode() == DaprMode.MEMORY ? "in-process" : dapr.connection().httpEndpoint())
                .build();
    }
}
