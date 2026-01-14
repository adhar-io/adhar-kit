package com.adhar.kit.config.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to refresh when configuration changes.
 *
 * <p>Automatically re-executes annotated methods when configuration is updated.</p>
 *
 * <p><b>Example - Refresh Cache:</b></p>
 * <pre>{@code
 * @Component
 * public class CacheManager {
 *
 *     private final ConfigManager configManager;
 *     private Cache cache;
 *
 *     @PostConstruct
 *     public void init() {
 *         initializeCache();
 *     }
 *
 *     @RefreshConfig(keys = {"cache.ttl", "cache.maxSize"})
 *     public void initializeCache() {
 *         int ttl = configManager.getProperty("cache.ttl", Integer.class, 300);
 *         int maxSize = configManager.getProperty("cache.maxSize", Integer.class, 1000);
 *
 *         cache = CacheBuilder.newBuilder()
 *             .expireAfterWrite(ttl, TimeUnit.SECONDS)
 *             .maximumSize(maxSize)
 *             .build();
 *
 *         log.info("Cache reinitialized: ttl={}, maxSize={}", ttl, maxSize);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - Update Client Configuration:</b></p>
 * <pre>{@code
 * @Service
 * public class ApiClientService {
 *
 *     private RestClient client;
 *
 *     @RefreshConfig(keys = {"api.timeout", "api.retries"})
 *     public void updateClientConfig() {
 *         int timeout = config.getProperty("api.timeout", Integer.class, 5000);
 *         int retries = config.getProperty("api.retries", Integer.class, 3);
 *
 *         client = RestClient.builder()
 *             .timeout(Duration.ofMillis(timeout))
 *             .retries(retries)
 *             .build();
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
public @interface RefreshConfig {

    /**
     * Configuration keys to watch for changes.
     * <p>
     * When any of these keys change, the method will be invoked.
     */
    String[] keys() default {};

    /**
     * Key prefix to watch.
     * <p>
     * Watches all keys starting with this prefix.
     */
    String prefix() default "";

    /**
     * Whether to refresh on startup.
     */
    boolean refreshOnStartup() default false;
}

