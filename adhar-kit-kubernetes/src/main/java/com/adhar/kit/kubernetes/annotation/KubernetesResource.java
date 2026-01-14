package com.adhar.kit.kubernetes.annotation;

import java.lang.annotation.*;

/**
 * Marks a class for automatic Kubernetes resource management.
 *
 * <p>Automatically manages CPU, memory, and other resource limits for the application.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @KubernetesResource(
 *     cpuRequest = "500m",
 *     cpuLimit = "1000m",
 *     memoryRequest = "512Mi",
 *     memoryLimit = "1Gi"
 * )
 * @Component
 * public class ResourceIntensiveService {
 *
 *     public void processLargeData() {
 *         // Kubernetes ensures resources are available
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
public @interface KubernetesResource {

    /**
     * CPU request (e.g., "500m" = 0.5 CPU cores).
     */
    String cpuRequest() default "250m";

    /**
     * CPU limit (e.g., "1000m" = 1 CPU core).
     */
    String cpuLimit() default "500m";

    /**
     * Memory request (e.g., "512Mi" = 512 megabytes).
     */
    String memoryRequest() default "256Mi";

    /**
     * Memory limit (e.g., "1Gi" = 1 gigabyte).
     */
    String memoryLimit() default "512Mi";

    /**
     * Storage request (e.g., "10Gi").
     */
    String storageRequest() default "";

    /**
     * Enable resource monitoring.
     */
    boolean monitorResources() default true;
}

