package com.adhar.adharkit.cache.annotation;

import java.lang.annotation.*;

/**
 * Refresh cache asynchronously in background.
 *
 * <p><b>Example - Background Refresh:</b></p>
 * <pre>{@code
 * @Service
 * public class ConfigService {
 *
 *     @CacheRefresh(
 *         cacheName = "system-config",
 *         refreshInterval = 5,
 *         refreshAsync = true
 *     )
 *     @Cacheable(cacheName = "system-config", key = "#configKey")
 *     public Config getConfig(String configKey) {
 *         return configRepository.findByKey(configKey);
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
public @interface CacheRefresh {

    /**
     * Cache name.
     */
    String cacheName();

    /**
     * Refresh interval (minutes).
     */
    long refreshInterval() default 10;

    /**
     * Refresh asynchronously.
     */
    boolean refreshAsync() default true;

    /**
     * Initial delay before first refresh (minutes).
     */
    long initialDelay() default 1;
}

