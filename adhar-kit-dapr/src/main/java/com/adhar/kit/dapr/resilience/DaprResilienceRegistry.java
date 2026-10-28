package com.adhar.kit.dapr.resilience;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One {@link DaprInvocationResilience} (retry, timeout and circuit breaker) per target app id.
 *
 * <p>Breakers are per target on purpose: with a single shared breaker, one failing dependency
 * opened the circuit for every other app this service calls, turning one outage into many.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprResilienceRegistry {

    private final ResilienceSettings settings;
    private final Map<String, DaprInvocationResilience> byTarget = new ConcurrentHashMap<>();

    public DaprResilienceRegistry() {
        this(ResilienceSettings.defaults());
    }

    public DaprResilienceRegistry(ResilienceSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
    }

    /**
     * The resilience of calls to one target app id, created on first use.
     *
     * @param appId the target app id
     * @return its resilience
     */
    public DaprInvocationResilience forTarget(String appId) {
        return byTarget.computeIfAbsent(appId == null ? "" : appId, id -> new DaprInvocationResilience(settings));
    }

    /** The settings every target starts with. */
    public ResilienceSettings settings() {
        return settings;
    }

    /** {@code true} while the breaker for {@code appId} rejects calls. */
    public boolean isCircuitOpen(String appId) {
        DaprInvocationResilience resilience = byTarget.get(appId);
        return resilience != null && resilience.isCircuitOpen();
    }
}
