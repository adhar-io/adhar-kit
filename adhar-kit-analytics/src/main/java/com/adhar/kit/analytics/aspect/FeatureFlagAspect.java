package com.adhar.kit.analytics.aspect;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.FeatureFlag;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect for processing @FeatureFlag annotations.
 *
 * <p>Provides A/B testing capabilities by checking feature flags before method execution
 * and optionally routing to fallback methods when flags are disabled.</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic feature flag checking via PostHog</li>
 *   <li>Exposure tracking for A/B test analysis</li>
 *   <li>Fallback method support for flag-gated features</li>
 *   <li>Conditional method execution based on flag state</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Service
 * public class RecommendationService {
 *
 *     @FeatureFlag(
 *         flag = "new-recommendation-algorithm",
 *         userIdParam = "userId",
 *         trackExposure = true,
 *         requireEnabled = true,
 *         fallbackMethod = "getLegacyRecommendations"
 *     )
 *     public List<Product> getRecommendations(String userId) {
 *         // New ML-based recommendations
 *         return mlRecommendations;
 *     }
 *
 *     public List<Product> getLegacyRecommendations(String userId) {
 *         // Old rule-based recommendations
 *         return legacyRecommendations;
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(50) // Execute before TrackEventAspect
@Slf4j
public class FeatureFlagAspect {

    private final AnalyticsFacade analytics;

    public FeatureFlagAspect() {
        this.analytics = AnalyticsFacade.getInstance();
    }

    /**
     * Package-private constructor for injecting a controllable
     * {@link AnalyticsFacade} (e.g. a Mockito mock) in tests, without going
     * through the process-wide singleton.
     */
    FeatureFlagAspect(AnalyticsFacade analytics) {
        this.analytics = analytics;
    }

    /**
     * Intercepts methods annotated with @FeatureFlag.
     *
     * @param joinPoint the join point
     * @param featureFlag the annotation
     * @return the method result or fallback result
     * @throws Throwable if method execution fails
     */
    @Around("@annotation(featureFlag)")
    public Object processFeatureFlag(ProceedingJoinPoint joinPoint, FeatureFlag featureFlag) throws Throwable {
        if (!analytics.isAvailable()) {
            log.debug("Analytics not available, proceeding with original method");
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Build parameter map
        Map<String, Object> params = buildParameterMap(
            signature.getParameterNames(),
            joinPoint.getArgs()
        );

        // Extract user ID
        String userId = extractUserId(params, featureFlag.userIdParam());
        if (userId == null) {
            log.warn("User ID not found for @FeatureFlag on {}, proceeding with original method",
                method.getName());
            return joinPoint.proceed();
        }

        // Check feature flag
        String flagKey = featureFlag.flag();
        boolean isEnabled = analytics.isFeatureEnabled(userId, flagKey);

        log.debug("Feature flag '{}' is {} for user {} on method {}",
            flagKey, isEnabled ? "enabled" : "disabled", userId, method.getName());

        // Track exposure if configured
        if (featureFlag.trackExposure()) {
            trackFeatureExposure(userId, flagKey, isEnabled);
        }

        // Handle based on flag state and configuration
        if (isEnabled) {
            // Feature is enabled, proceed with original method
            return joinPoint.proceed();
        } else {
            // Feature is disabled
            if (featureFlag.requireEnabled()) {
                // Flag requires enabled state - try fallback or return null
                String fallbackMethodName = featureFlag.fallbackMethod();
                if (!fallbackMethodName.isEmpty()) {
                    return invokeFallbackMethod(joinPoint, fallbackMethodName, params);
                } else {
                    // Return null or default value for the return type
                    return getDefaultReturnValue(method.getReturnType());
                }
            } else {
                // Flag doesn't require enabled state, proceed normally
                return joinPoint.proceed();
            }
        }
    }

    /**
     * Tracks feature flag exposure for A/B testing analysis.
     */
    private void trackFeatureExposure(String userId, String flagKey, boolean isEnabled) {
        try {
            analytics.track(userId, "$feature_flag_called", Map.of(
                "$feature_flag", flagKey,
                "$feature_flag_response", isEnabled
            ));
        } catch (Exception e) {
            log.error("Failed to track feature flag exposure", e);
        }
    }

    /**
     * Invokes the fallback method when the feature flag is disabled.
     */
    private Object invokeFallbackMethod(ProceedingJoinPoint joinPoint,
                                        String fallbackMethodName,
                                        Map<String, Object> params) throws Throwable {
        try {
            Object target = joinPoint.getTarget();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();

            // Find fallback method with same parameters
            Method fallbackMethod = findFallbackMethod(
                target.getClass(),
                fallbackMethodName,
                signature.getParameterTypes()
            );

            if (fallbackMethod != null) {
                log.debug("Invoking fallback method: {}", fallbackMethodName);
                fallbackMethod.setAccessible(true);
                return fallbackMethod.invoke(target, joinPoint.getArgs());
            } else {
                log.warn("Fallback method '{}' not found with matching parameters, returning null",
                    fallbackMethodName);
                return getDefaultReturnValue(signature.getMethod().getReturnType());
            }
        } catch (Exception e) {
            log.error("Failed to invoke fallback method: " + fallbackMethodName, e);
            throw e;
        }
    }

    /**
     * Finds the fallback method in the target class.
     */
    private Method findFallbackMethod(Class<?> targetClass, String methodName, Class<?>[] paramTypes) {
        try {
            return targetClass.getMethod(methodName, paramTypes);
        } catch (NoSuchMethodException e) {
            // Try declared methods (including private)
            try {
                return targetClass.getDeclaredMethod(methodName, paramTypes);
            } catch (NoSuchMethodException ex) {
                // Try without exact parameter match
                for (Method method : targetClass.getDeclaredMethods()) {
                    if (method.getName().equals(methodName)) {
                        return method;
                    }
                }
                return null;
            }
        }
    }

    /**
     * Gets a default return value for the given type.
     */
    private Object getDefaultReturnValue(Class<?> returnType) {
        if (returnType == void.class || returnType == Void.class) {
            return null;
        }
        if (returnType == boolean.class || returnType == Boolean.class) {
            return false;
        }
        if (returnType == int.class || returnType == Integer.class) {
            return 0;
        }
        if (returnType == long.class || returnType == Long.class) {
            return 0L;
        }
        if (returnType == double.class || returnType == Double.class) {
            return 0.0;
        }
        if (returnType == float.class || returnType == Float.class) {
            return 0.0f;
        }
        if (returnType == char.class || returnType == Character.class) {
            return '\0';
        }
        if (returnType == byte.class || returnType == Byte.class) {
            return (byte) 0;
        }
        if (returnType == short.class || returnType == Short.class) {
            return (short) 0;
        }
        // For objects, return null
        return null;
    }

    /**
     * Builds parameter map from method parameters.
     */
    private Map<String, Object> buildParameterMap(String[] paramNames, Object[] paramValues) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            params.put(paramNames[i], paramValues[i]);
        }
        return params;
    }

    /**
     * Extracts user ID from parameters.
     */
    private String extractUserId(Map<String, Object> params, String userIdParam) {
        // Support nested property access (e.g., "user.id")
        if (userIdParam.contains(".")) {
            String[] parts = userIdParam.split("\\.", 2);
            Object parent = params.get(parts[0]);
            if (parent != null) {
                try {
                    String getterName = "get" + Character.toUpperCase(parts[1].charAt(0)) + parts[1].substring(1);
                    Method getter = parent.getClass().getMethod(getterName);
                    Object value = getter.invoke(parent);
                    return value != null ? value.toString() : null;
                } catch (Exception e) {
                    log.debug("Could not extract nested userId: {}", e.getMessage());
                }
            }
            return null;
        }

        Object userId = params.get(userIdParam);
        return userId != null ? userId.toString() : null;
    }
}
