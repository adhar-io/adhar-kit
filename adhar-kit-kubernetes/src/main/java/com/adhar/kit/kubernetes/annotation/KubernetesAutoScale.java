package com.adhar.kit.kubernetes.annotation;

import java.lang.annotation.*;

/**
 * Enables automatic horizontal pod autoscaling.
 *
 * <p>Automatically scales pods based on CPU, memory, or custom metrics.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @KubernetesAutoScale(
 *     minReplicas = 2,
 *     maxReplicas = 10,
 *     targetCpuUtilization = 70,
 *     targetMemoryUtilization = 80
 * )
 * @Component
 * public class AutoScalingService {
 *
 *     public void handleTraffic() {
 *         // Automatically scales based on load
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface KubernetesAutoScale {

    /**
     * Minimum number of replicas.
     */
    int minReplicas() default 1;

    /**
     * Maximum number of replicas.
     */
    int maxReplicas() default 10;

    /**
     * Target CPU utilization percentage (0-100).
     */
    int targetCpuUtilization() default 80;

    /**
     * Target memory utilization percentage (0-100).
     */
    int targetMemoryUtilization() default 80;

    /**
     * Scale up stabilization window in seconds.
     */
    int scaleUpStabilization() default 0;

    /**
     * Scale down stabilization window in seconds.
     */
    int scaleDownStabilization() default 300;

    /**
     * Custom metrics for scaling.
     */
    String[] customMetrics() default {};
}

