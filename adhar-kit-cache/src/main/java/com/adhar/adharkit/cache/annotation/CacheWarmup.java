package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Warm up cache on application startup.
 *
 * <p>Pre-loads cache with frequently accessed data.</p>
 *
 * <p><b>Example - Preload Reference Data:</b></p>
 * <pre>{@code
 * @Service
 * public class ConfigService {
 *
 *     @CacheWarmup(
 *         cacheName = "config",
 *         warmupMethod = "loadAllConfigs"
 *     )
 *     public Map<String, Config> getAllConfigs() {
 *         return configRepository.findAll();
 *     }
 *
 *     private void loadAllConfigs(CacheFacade cache) {
 *         List<Config> configs = configRepository.findAll();
 *         configs.forEach(c -> cache.put(c.getKey(), c));
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheWarmup {

    /**
     * Cache name to warm up.
     */
    String cacheName();

    /**
     * Method to execute for warmup.
     */
    String warmupMethod() default "";

    /**
     * Execute warmup asynchronously.
     */
    boolean async() default true;

    /**
     * Delay before warmup (milliseconds).
     */
    long delay() default 0;
}

