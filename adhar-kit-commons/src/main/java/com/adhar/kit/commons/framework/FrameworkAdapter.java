package com.adhar.kit.commons.framework;

/**
 * Framework-specific adapter interface for Adhar Kit components.
 *
 * <p>This interface allows components to provide framework-specific implementations
 * while maintaining a common API. Each framework (Spring Boot, Quarkus, Micronaut)
 * can have its own adapter implementation.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * @Service
 * @ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
 * public class SpringCacheAdapter implements FrameworkAdapter<CacheService> {
 *
 *     @Override
 *     public Framework getSupportedFramework() {
 *         return Framework.SPRING_BOOT;
 *     }
 *
 *     @Override
 *     public CacheService getService() {
 *         return this;
 *     }
 * }
 * }</pre>
 *
 * @param <T> the service type provided by this adapter
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public interface FrameworkAdapter<T> {

    /**
     * Gets the framework supported by this adapter.
     *
     * @return the supported framework
     */
    Framework getSupportedFramework();

    /**
     * Gets the service instance provided by this adapter.
     *
     * @return the service instance
     */
    T getService();
}

