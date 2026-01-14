package com.adhar.kit.health.registry;

import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.model.Health;
import com.adhar.kit.health.model.HealthResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Central registry for all health indicators.
 *
 * <p>Manages health indicator registration and execution.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Auto-discovery of health indicators</li>
 *   <li>Parallel health check execution</li>
 *   <li>Timeout handling</li>
 *   <li>Result aggregation</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * HealthRegistry registry = new HealthRegistry();
 * registry.register(new DatabaseHealthIndicator(dataSource, config));
 * registry.register(new RedisHealthIndicator(redisTemplate, config));
 *
 * HealthResponse response = registry.checkHealth();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class HealthRegistry {

    private final Map<String, AdharHealthIndicator> indicators = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * Registers a health indicator.
     *
     * @param indicator health indicator
     */
    public void register(AdharHealthIndicator indicator) {
        String name = indicator.getName();
        indicators.put(name, indicator);
        log.info("Registered health indicator: {}", name);
    }

    /**
     * Unregisters a health indicator.
     *
     * @param name indicator name
     */
    public void unregister(String name) {
        indicators.remove(name);
        log.info("Unregistered health indicator: {}", name);
    }

    /**
     * Checks health of all indicators.
     *
     * @return aggregated health response
     */
    public HealthResponse checkHealth() {
        return checkHealth(5000); // 5 second default timeout
    }

    /**
     * Checks health of all indicators with timeout.
     *
     * @param timeoutMillis timeout in milliseconds
     * @return aggregated health response
     */
    public HealthResponse checkHealth(long timeoutMillis) {
        HealthResponse.HealthResponseBuilder response = HealthResponse.builder();

        // Execute all health checks in parallel
        Map<String, Future<Health>> futures = new ConcurrentHashMap<>();

        for (Map.Entry<String, AdharHealthIndicator> entry : indicators.entrySet()) {
            String name = entry.getKey();
            AdharHealthIndicator indicator = entry.getValue();

            Future<Health> future = executor.submit(() -> {
                try {
                    return indicator.check();
                } catch (Exception e) {
                    log.error("Health check failed for {}", name, e);
                    return Health.down(e)
                        .component(name)
                        .build();
                }
            });

            futures.put(name, future);
        }

        // Collect results
        Map<String, Health> results = new ConcurrentHashMap<>();
        boolean allHealthy = true;

        for (Map.Entry<String, Future<Health>> entry : futures.entrySet()) {
            String name = entry.getKey();
            Future<Health> future = entry.getValue();

            try {
                Health health = future.get(timeoutMillis, TimeUnit.MILLISECONDS);
                results.put(name, health);

                if (health.isDown()) {
                    allHealthy = false;
                }
            } catch (TimeoutException e) {
                log.warn("Health check timeout for {}", name);
                Health timeoutHealth = Health.down()
                    .component(name)
                    .error("Health check timeout")
                    .build();
                results.put(name, timeoutHealth);
                allHealthy = false;
            } catch (Exception e) {
                log.error("Failed to get health check result for {}", name, e);
                Health errorHealth = Health.down(e)
                    .component(name)
                    .build();
                results.put(name, errorHealth);
                allHealthy = false;
            }
        }

        // Determine overall status
        Health.Status overallStatus = allHealthy ? Health.Status.UP : Health.Status.DOWN;

        return response
            .status(overallStatus)
            .components(results)
            .build();
    }

    /**
     * Gets a specific indicator.
     *
     * @param name indicator name
     * @return health indicator or null
     */
    public AdharHealthIndicator getIndicator(String name) {
        return indicators.get(name);
    }

    /**
     * Gets all registered indicators.
     *
     * @return map of indicators
     */
    public Map<String, AdharHealthIndicator> getIndicators() {
        return new ConcurrentHashMap<>(indicators);
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

