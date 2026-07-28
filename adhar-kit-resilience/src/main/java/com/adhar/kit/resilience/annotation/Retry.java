package com.adhar.kit.resilience.annotation;

import java.lang.annotation.*;

/**
 * Annotation to apply retry pattern to methods.
 * Uses Resilience4j retry implementation.
 *
 * <p>Numeric attributes default to {@code -1}, which means "use the default (or
 * property-driven) configuration of the registry". Any explicitly set attribute is
 * honored when the named retry instance is first created. If an instance with the
 * same name already exists (e.g. configured via
 * {@code adhar.resilience.retry.<name>.*} properties), the existing configuration
 * wins.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retry {

    /**
     * Name of the retry instance.
     * @return retry name
     */
    String name() default "default";

    /**
     * Fallback method name in the same class.
     * @return fallback method name
     */
    String fallbackMethod() default "";

    /**
     * Enables the "last known good" fallback cache for this method. When {@code true},
     * successful results are cached and, if a later call fails after all retries are
     * exhausted with no {@link #fallbackMethod()} configured, the most recent cached result
     * is returned instead of propagating the failure. Requires the fallback cache bean.
     * @return whether the fallback cache is enabled for this method
     */
    boolean fallbackCache() default false;

    /**
     * Maximum number of attempts (including the initial call).
     * {@code -1} means use default config (3).
     * @return max attempts
     */
    int maxAttempts() default -1;

    /**
     * Wait duration between retries in milliseconds.
     * {@code -1} means use default config (500ms).
     * @return wait duration
     */
    long waitDuration() default -1L;
}
