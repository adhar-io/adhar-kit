package com.adhar.kit.analytics.annotation;

import java.lang.annotation.*;

/**
 * Enables A/B testing using feature flags.
 *
 * <p><b>Example - A/B Test:</b></p>
 * <pre>{@code
 * @Service
 * public class RecommendationService {
 *
 *     @FeatureFlag(
 *         flag = "new-recommendation-algorithm",
 *         userIdParam = "userId",
 *         trackExposure = true
 *     )
 *     public List<Product> getRecommendations(String userId) {
 *         // If flag is enabled, use new algorithm
 *         // Otherwise use old algorithm
 *         return recommendations;
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
public @interface FeatureFlag {

    /**
     * Feature flag key.
     */
    String flag();

    /**
     * Parameter name containing the user ID.
     */
    String userIdParam() default "userId";

    /**
     * Track when user is exposed to this feature.
     */
    boolean trackExposure() default true;

    /**
     * Execute method only if flag is enabled.
     * Otherwise return null or default value.
     */
    boolean requireEnabled() default false;

    /**
     * Alternative method to call if flag is disabled.
     */
    String fallbackMethod() default "";
}

