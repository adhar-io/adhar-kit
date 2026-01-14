package com.adhar.kit.core.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to retry on failure with exponential backoff.
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Retry(maxAttempts = 3, backoff = @Backoff(delay = 1000))
 * public User getUserFromExternalApi(String userId) {
 *     return externalApi.getUser(userId);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Retry {

    /**
     * Maximum number of retry attempts.
     */
    int maxAttempts() default 3;

    /**
     * Backoff configuration.
     */
    Backoff backoff() default @Backoff;

    /**
     * Exceptions to retry on.
     */
    Class<? extends Throwable>[] retryOn() default {Exception.class};

    /**
     * Backoff configuration.
     */
    @interface Backoff {
        /**
         * Initial delay in milliseconds.
         */
        long delay() default 1000;

        /**
         * Maximum delay in milliseconds.
         */
        long maxDelay() default 30000;

        /**
         * Backoff multiplier.
         */
        double multiplier() default 2.0;
    }
}

