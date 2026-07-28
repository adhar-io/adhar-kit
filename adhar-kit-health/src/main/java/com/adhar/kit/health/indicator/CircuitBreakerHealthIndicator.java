package com.adhar.kit.health.indicator;

import com.adhar.kit.health.config.AdharHealthProperties;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.spi.CircuitBreakerStateProvider;
import com.adhar.kit.health.spi.CircuitBreakerStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * Health indicator that reports on circuit-breaker state.
 *
 * <p>Aggregates breaker snapshots discovered through one or more
 * {@link CircuitBreakerStateProvider}s (an SPI), so the indicator works without a hard
 * dependency on any circuit-breaker library.</p>
 *
 * <p><b>Status mapping:</b></p>
 * <ul>
 *   <li>Any OPEN / FORCED_OPEN breaker &rarr; DOWN</li>
 *   <li>Otherwise any HALF_OPEN breaker &rarr; OUT_OF_SERVICE (when configured as degraded)</li>
 *   <li>Otherwise &rarr; UP</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
public class CircuitBreakerHealthIndicator implements AdharHealthIndicator {

    /** Indicator name used in health responses. */
    public static final String NAME = "circuitBreakers";

    private final List<CircuitBreakerStateProvider> providers;
    private final AdharHealthProperties.CircuitBreakerConfig config;

    /**
     * Creates the indicator with default configuration.
     *
     * @param providers circuit-breaker state providers
     */
    public CircuitBreakerHealthIndicator(List<CircuitBreakerStateProvider> providers) {
        this(providers, new AdharHealthProperties.CircuitBreakerConfig());
    }

    /**
     * Creates the indicator.
     *
     * @param providers circuit-breaker state providers
     * @param config    circuit-breaker health configuration
     */
    public CircuitBreakerHealthIndicator(List<CircuitBreakerStateProvider> providers,
                                         AdharHealthProperties.CircuitBreakerConfig config) {
        this.providers = providers == null ? List.of() : providers;
        this.config = config;
    }

    @Override
    public Health check() {
        List<CircuitBreakerStatus> all = collectStates();

        List<String> open = new ArrayList<>();
        List<String> halfOpen = new ArrayList<>();
        for (CircuitBreakerStatus status : all) {
            if (status.isOpen()) {
                open.add(status.name());
            } else if (status.isHalfOpen()) {
                halfOpen.add(status.name());
            }
        }

        Health.Status overall;
        if (!open.isEmpty()) {
            overall = Health.Status.DOWN;
        } else if (!halfOpen.isEmpty() && config.isTreatHalfOpenAsDegraded()) {
            overall = Health.Status.OUT_OF_SERVICE;
        } else {
            overall = Health.Status.UP;
        }

        Health.HealthBuilder builder = Health.builder()
                .status(overall)
                .component(NAME)
                .withDetail("total", all.size())
                .withDetail("open", open)
                .withDetail("halfOpen", halfOpen);
        if (!open.isEmpty()) {
            builder.error("Open circuit breakers: " + String.join(", ", open));
        }
        return builder.build();
    }

    private List<CircuitBreakerStatus> collectStates() {
        List<CircuitBreakerStatus> all = new ArrayList<>();
        for (CircuitBreakerStateProvider provider : providers) {
            try {
                List<CircuitBreakerStatus> states = provider.states();
                if (states != null) {
                    all.addAll(states);
                }
            } catch (Exception e) {
                log.warn("Circuit-breaker state provider {} failed", provider.getClass().getName(), e);
            }
        }
        return all;
    }

    @Override
    public String getName() {
        return NAME;
    }
}
