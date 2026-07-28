package com.adhar.kit.health.registry;

import com.adhar.kit.health.event.HealthTransition;
import com.adhar.kit.health.event.HealthTransitionListener;
import com.adhar.kit.health.history.HealthHistory;
import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.model.HealthResponse;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Central registry for all health indicators.
 *
 * <p>Manages health indicator registration, grouping and execution.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Named groups ({@code liveness}, {@code readiness}, {@code startup}, {@code default})</li>
 *   <li>Worst-of status aggregation: DOWN &gt; OUT_OF_SERVICE &gt; UNKNOWN &gt; UP</li>
 *   <li>Non-critical indicators degrade the response without failing the group</li>
 *   <li>Parallel health check execution with timeout handling</li>
 *   <li>Optional TTL result caching for cheap repeated probe scrapes</li>
 *   <li>Health transition events and bounded history with flapping detection</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * HealthRegistry registry = new HealthRegistry();
 * registry.register(new DatabaseHealthIndicator(dataSource, config), HealthRegistry.READINESS_GROUP);
 * registry.register(new MemoryHealthIndicator(), false, HealthRegistry.LIVENESS_GROUP);
 *
 * HealthResponse overall = registry.checkHealth();
 * HealthResponse readiness = registry.checkGroup(HealthRegistry.READINESS_GROUP);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class HealthRegistry {

    /** Default group for indicators registered without an explicit group. */
    public static final String DEFAULT_GROUP = "default";

    /** Liveness probe group. */
    public static final String LIVENESS_GROUP = "liveness";

    /** Readiness probe group. */
    public static final String READINESS_GROUP = "readiness";

    /** Startup probe group. */
    public static final String STARTUP_GROUP = "startup";

    /** Response detail key listing degraded (non-critical, not UP) indicators. */
    public static final String DEGRADED_DETAIL = "degraded";

    /** Response detail key carrying the weighted health score [0..1] (weighted mode only). */
    public static final String WEIGHTED_SCORE_DETAIL = "weightedScore";

    private static final String ALL_CACHE_KEY = "__all__";
    private static final long DEFAULT_TIMEOUT_MILLIS = 5000;

    private record Registration(AdharHealthIndicator indicator, Set<String> groups, boolean critical, double weight) {
    }

    private record CachedResponse(HealthResponse response, long expiresAtNanos) {
    }

    private final Map<String, Registration> registrations = new ConcurrentHashMap<>();
    private final Map<String, CachedResponse> cache = new ConcurrentHashMap<>();
    private final Map<String, Health.Status> lastStatuses = new ConcurrentHashMap<>();
    private final List<HealthTransitionListener> listeners = new CopyOnWriteArrayList<>();
    private final HealthHistory history;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** Cache TTL in milliseconds; {@code 0} disables caching. */
    private volatile long cacheTtlMillis;

    /** Number of transitions within the flapping window that flags an indicator as flapping. */
    private volatile int flappingThreshold = 5;

    /** Flapping detection window in milliseconds. */
    private volatile long flappingWindowMillis = 60_000;

    /**
     * Weighted-score threshold at or above which a group aggregates to UP.
     * Only consulted when at least one selected indicator carries a weight.
     */
    private volatile double weightedUpThreshold = 1.0;

    /**
     * Weighted-score threshold at or below which a group aggregates to DOWN.
     * Scores strictly between {@link #weightedDownThreshold} and
     * {@link #weightedUpThreshold} aggregate to the degraded band
     * ({@link Health.Status#OUT_OF_SERVICE}).
     */
    private volatile double weightedDownThreshold = 0.0;

    /**
     * Creates a registry with caching disabled and default history capacity.
     */
    public HealthRegistry() {
        this(0, HealthHistory.DEFAULT_CAPACITY);
    }

    /**
     * Creates a registry.
     *
     * @param cacheTtlMillis  result cache TTL in milliseconds (0 = disabled)
     * @param historyCapacity maximum retained health transitions
     */
    public HealthRegistry(long cacheTtlMillis, int historyCapacity) {
        this.cacheTtlMillis = Math.max(0, cacheTtlMillis);
        this.history = new HealthHistory(historyCapacity);
    }

    /**
     * Registers a critical health indicator into the default group.
     *
     * @param indicator health indicator
     */
    public void register(AdharHealthIndicator indicator) {
        register(indicator, true, DEFAULT_GROUP);
    }

    /**
     * Registers a critical health indicator into the given groups.
     *
     * @param indicator health indicator
     * @param groups    group names (default group when empty)
     */
    public void register(AdharHealthIndicator indicator, String... groups) {
        register(indicator, true, groups);
    }

    /**
     * Registers a health indicator.
     *
     * <p>Non-critical ({@code critical == false}) indicators are reported in the
     * response components and, when not UP, listed under the {@code degraded}
     * detail — but they never fail the aggregated group status.</p>
     *
     * @param indicator health indicator
     * @param critical  whether the indicator participates in status aggregation
     * @param groups    group names (default group when empty)
     */
    public void register(AdharHealthIndicator indicator, boolean critical, String... groups) {
        register(indicator, critical, 0.0, groups);
    }

    /**
     * Registers a health indicator with an aggregation weight.
     *
     * <p>A positive {@code weight} opts the indicator into <b>weighted aggregation</b>:
     * whenever any selected indicator carries a weight, the group status is derived
     * from the weighted health score (see {@link #setWeightedThresholds(double, double)})
     * rather than the critical/non-critical worst-of rule. A {@code weight} of {@code 0}
     * leaves the indicator on the classic critical/non-critical path.</p>
     *
     * @param indicator health indicator
     * @param critical  whether the indicator participates in classic status aggregation
     *                  (also folded into weighted results as a hard failure)
     * @param weight    non-negative aggregation weight ({@code 0} = unweighted)
     * @param groups    group names (default group when empty)
     */
    public void register(AdharHealthIndicator indicator, boolean critical, double weight, String... groups) {
        if (weight < 0) {
            throw new IllegalArgumentException("weight must not be negative");
        }
        String name = indicator.getName();
        Set<String> groupSet = (groups == null || groups.length == 0)
                ? Set.of(DEFAULT_GROUP)
                : Set.copyOf(new LinkedHashSet<>(Arrays.asList(groups)));
        registrations.put(name, new Registration(indicator, groupSet, critical, weight));
        cache.clear();
        log.info("Registered health indicator: {} (groups={}, critical={}, weight={})",
                name, groupSet, critical, weight);
    }

    /**
     * Sets the aggregation weight of an already-registered indicator.
     *
     * <p>Convenience for applying config-driven weights after indicators have been
     * registered. No-op when the indicator is unknown.</p>
     *
     * @param name   indicator name
     * @param weight non-negative aggregation weight ({@code 0} = unweighted)
     * @return true when the weight was applied
     */
    public boolean setWeight(String name, double weight) {
        if (weight < 0) {
            throw new IllegalArgumentException("weight must not be negative");
        }
        Registration existing = registrations.get(name);
        if (existing == null) {
            return false;
        }
        registrations.put(name, new Registration(existing.indicator(), existing.groups(), existing.critical(), weight));
        cache.clear();
        return true;
    }

    /**
     * Gets the aggregation weight of an indicator.
     *
     * @param name indicator name
     * @return the weight, or {@code 0} when unweighted or unknown
     */
    public double getWeight(String name) {
        Registration registration = registrations.get(name);
        return registration == null ? 0.0 : registration.weight();
    }

    /**
     * Configures the weighted-aggregation thresholds.
     *
     * <p>With a weighted score {@code s} in {@code [0..1]}: {@code s >= up} aggregates
     * to UP, {@code s <= down} aggregates to DOWN, and anything in between aggregates
     * to the degraded band ({@link Health.Status#OUT_OF_SERVICE}).</p>
     *
     * @param up   UP threshold (inclusive)
     * @param down DOWN threshold (inclusive), must not exceed {@code up}
     */
    public void setWeightedThresholds(double up, double down) {
        if (down > up) {
            throw new IllegalArgumentException("down threshold must not exceed up threshold");
        }
        this.weightedUpThreshold = up;
        this.weightedDownThreshold = down;
        cache.clear();
    }

    /**
     * Unregisters a health indicator.
     *
     * @param name indicator name
     * @return true if the indicator was registered
     */
    public boolean unregister(String name) {
        boolean removed = registrations.remove(name) != null;
        if (removed) {
            lastStatuses.remove(name);
            cache.clear();
            log.info("Unregistered health indicator: {}", name);
        }
        return removed;
    }

    /**
     * Checks health of all indicators (all groups).
     *
     * @return aggregated health response
     */
    public HealthResponse checkHealth() {
        return checkHealth(DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Checks health of all indicators (all groups) with timeout.
     *
     * @param timeoutMillis timeout in milliseconds
     * @return aggregated health response
     */
    public HealthResponse checkHealth(long timeoutMillis) {
        return check(null, timeoutMillis);
    }

    /**
     * Checks health of all indicators in a named group.
     *
     * <p>An empty group aggregates to UP.</p>
     *
     * @param group group name (e.g. {@link #LIVENESS_GROUP}, {@link #READINESS_GROUP})
     * @return aggregated health response for the group
     */
    public HealthResponse checkGroup(String group) {
        return checkGroup(group, DEFAULT_TIMEOUT_MILLIS);
    }

    /**
     * Checks health of all indicators in a named group with timeout.
     *
     * @param group         group name
     * @param timeoutMillis timeout in milliseconds
     * @return aggregated health response for the group
     */
    public HealthResponse checkGroup(String group, long timeoutMillis) {
        if (group == null) {
            throw new IllegalArgumentException("group must not be null");
        }
        return check(group, timeoutMillis);
    }

    private HealthResponse check(String group, long timeoutMillis) {
        String cacheKey = group == null ? ALL_CACHE_KEY : group;
        long ttl = cacheTtlMillis;

        if (ttl > 0) {
            CachedResponse cached = cache.get(cacheKey);
            if (cached != null && System.nanoTime() < cached.expiresAtNanos()) {
                return cached.response();
            }
        }

        Map<String, Registration> selected = selectRegistrations(group);
        Map<String, Health> results = execute(selected, timeoutMillis);
        recordTransitions(results);

        List<String> degraded = new ArrayList<>();
        List<Health.Status> criticalStatuses = new ArrayList<>();
        double weightSum = 0.0;
        double weightedValue = 0.0;
        boolean weighted = false;
        for (Map.Entry<String, Health> entry : results.entrySet()) {
            Registration registration = selected.get(entry.getKey());
            Health.Status status = entry.getValue().getStatus();
            if (registration != null && registration.weight() > 0) {
                weighted = true;
                weightSum += registration.weight();
                weightedValue += registration.weight() * healthValue(status);
                if (status != Health.Status.UP) {
                    degraded.add(entry.getKey());
                }
            } else if (registration != null && !registration.critical()) {
                if (status != Health.Status.UP) {
                    degraded.add(entry.getKey());
                }
            } else {
                criticalStatuses.add(status);
            }
        }

        Health.Status overall;
        Double score = null;
        if (weighted) {
            score = weightSum == 0.0 ? 1.0 : weightedValue / weightSum;
            // Fold in any unweighted critical indicators as hard failures via worst-of.
            overall = worstOf(Arrays.asList(weightedStatus(score), worstOf(criticalStatuses)));
        } else {
            overall = worstOf(criticalStatuses);
        }

        HealthResponse response = HealthResponse.builder()
                .status(overall)
                .components(results)
                .build();
        if (score != null) {
            response.addDetail(WEIGHTED_SCORE_DETAIL, score);
        }
        if (!degraded.isEmpty()) {
            degraded.sort(String::compareTo);
            response.addDetail(DEGRADED_DETAIL, degraded);
        }

        if (ttl > 0) {
            cache.put(cacheKey, new CachedResponse(response, System.nanoTime() + ttl * 1_000_000L));
        }
        return response;
    }

    private Map<String, Registration> selectRegistrations(String group) {
        Map<String, Registration> selected = new ConcurrentHashMap<>();
        for (Map.Entry<String, Registration> entry : registrations.entrySet()) {
            if (group == null || entry.getValue().groups().contains(group)) {
                selected.put(entry.getKey(), entry.getValue());
            }
        }
        return selected;
    }

    private Map<String, Health> execute(Map<String, Registration> selected, long timeoutMillis) {
        Map<String, Future<Health>> futures = new ConcurrentHashMap<>();
        for (Map.Entry<String, Registration> entry : selected.entrySet()) {
            String name = entry.getKey();
            AdharHealthIndicator indicator = entry.getValue().indicator();
            futures.put(name, executor.submit(() -> {
                try {
                    return indicator.check();
                } catch (Exception e) {
                    log.error("Health check failed for {}", name, e);
                    return Health.down(e)
                            .component(name)
                            .build();
                }
            }));
        }

        Map<String, Health> results = new ConcurrentHashMap<>();
        for (Map.Entry<String, Future<Health>> entry : futures.entrySet()) {
            String name = entry.getKey();
            try {
                results.put(name, entry.getValue().get(timeoutMillis, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                log.warn("Health check timeout for {}", name);
                results.put(name, Health.down()
                        .component(name)
                        .error("Health check timeout")
                        .build());
            } catch (Exception e) {
                log.error("Failed to get health check result for {}", name, e);
                results.put(name, Health.down(e)
                        .component(name)
                        .build());
            }
        }
        return results;
    }

    /**
     * Aggregates statuses using worst-of ordering: DOWN &gt; OUT_OF_SERVICE &gt; UNKNOWN &gt; UP.
     *
     * <p>An empty collection aggregates to UP.</p>
     *
     * @param statuses statuses to aggregate
     * @return the worst status
     */
    public static Health.Status worstOf(Collection<Health.Status> statuses) {
        Health.Status worst = Health.Status.UP;
        for (Health.Status status : statuses) {
            if (severity(status) > severity(worst)) {
                worst = status;
            }
        }
        return worst;
    }

    private static int severity(Health.Status status) {
        return switch (status) {
            case DOWN -> 3;
            case OUT_OF_SERVICE -> 2;
            case UNKNOWN -> 1;
            case UP -> 0;
        };
    }

    /**
     * Maps a status to a health value in {@code [0..1]} for weighted scoring:
     * UP = 1.0, UNKNOWN / OUT_OF_SERVICE = 0.5 (partial), DOWN = 0.0.
     *
     * @param status status to score
     * @return health value in {@code [0..1]}
     */
    private static double healthValue(Health.Status status) {
        return switch (status) {
            case UP -> 1.0;
            case UNKNOWN, OUT_OF_SERVICE -> 0.5;
            case DOWN -> 0.0;
        };
    }

    /**
     * Maps a weighted score to a status using the configured thresholds.
     *
     * @param score weighted score in {@code [0..1]}
     * @return UP, OUT_OF_SERVICE (degraded band) or DOWN
     */
    private Health.Status weightedStatus(double score) {
        if (score >= weightedUpThreshold) {
            return Health.Status.UP;
        }
        if (score <= weightedDownThreshold) {
            return Health.Status.DOWN;
        }
        return Health.Status.OUT_OF_SERVICE;
    }

    private void recordTransitions(Map<String, Health> results) {
        for (Map.Entry<String, Health> entry : results.entrySet()) {
            String name = entry.getKey();
            Health.Status current = entry.getValue().getStatus();
            Health.Status previous = lastStatuses.put(name, current);
            if (previous != current) {
                HealthTransition transition = new HealthTransition(name, previous, current, Instant.now());
                history.record(transition);
                notifyListeners(transition);
            }
        }
    }

    private void notifyListeners(HealthTransition transition) {
        for (HealthTransitionListener listener : listeners) {
            try {
                listener.onTransition(transition);
            } catch (Exception e) {
                log.warn("Health transition listener failed for {}", transition.indicator(), e);
            }
        }
    }

    /**
     * Adds a health transition listener.
     *
     * @param listener listener invoked on status changes
     */
    public void addTransitionListener(HealthTransitionListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes a health transition listener.
     *
     * @param listener listener to remove
     * @return true if the listener was registered
     */
    public boolean removeTransitionListener(HealthTransitionListener listener) {
        return listeners.remove(listener);
    }

    /**
     * Gets the bounded transition history.
     *
     * @return health history
     */
    public HealthHistory getHistory() {
        return history;
    }

    /**
     * Detects whether an indicator is flapping using the configured
     * threshold and window.
     *
     * @param indicator indicator name
     * @return true when the indicator is flapping
     */
    public boolean isFlapping(String indicator) {
        return history.isFlapping(indicator, flappingThreshold, Duration.ofMillis(flappingWindowMillis));
    }

    /**
     * Sets the result cache TTL. Clears any cached results.
     *
     * @param cacheTtlMillis TTL in milliseconds (0 disables caching)
     */
    public void setCacheTtlMillis(long cacheTtlMillis) {
        this.cacheTtlMillis = Math.max(0, cacheTtlMillis);
        cache.clear();
    }

    /**
     * Gets the result cache TTL in milliseconds.
     *
     * @return cache TTL (0 = disabled)
     */
    public long getCacheTtlMillis() {
        return cacheTtlMillis;
    }

    /**
     * Invalidates all cached health results.
     */
    public void invalidateCache() {
        cache.clear();
    }

    /**
     * Configures the flapping detector.
     *
     * @param threshold    number of transitions considered flapping
     * @param windowMillis look-back window in milliseconds
     */
    public void configureFlapping(int threshold, long windowMillis) {
        this.flappingThreshold = threshold;
        this.flappingWindowMillis = windowMillis;
    }

    /**
     * Gets a specific indicator.
     *
     * @param name indicator name
     * @return health indicator or null
     */
    public AdharHealthIndicator getIndicator(String name) {
        Registration registration = registrations.get(name);
        return registration == null ? null : registration.indicator();
    }

    /**
     * Gets all registered indicators.
     *
     * @return map of indicators
     */
    public Map<String, AdharHealthIndicator> getIndicators() {
        Map<String, AdharHealthIndicator> result = new ConcurrentHashMap<>();
        registrations.forEach((name, registration) -> result.put(name, registration.indicator()));
        return result;
    }

    /**
     * Gets the group names an indicator belongs to.
     *
     * @param name indicator name
     * @return group names, or an empty set when not registered
     */
    public Set<String> getGroups(String name) {
        Registration registration = registrations.get(name);
        return registration == null ? Set.of() : registration.groups();
    }

    /**
     * Checks whether an indicator is critical.
     *
     * @param name indicator name
     * @return true when the indicator participates in status aggregation
     */
    public boolean isCritical(String name) {
        Registration registration = registrations.get(name);
        return registration != null && registration.critical();
    }

    /**
     * Shuts down the registry.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}
