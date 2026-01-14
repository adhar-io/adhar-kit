package com.adhar.kit.kubernetes.integration;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Micronaut integration for Adhar Kubernetes.
 *
 * <p>Provides automatic Kubernetes configuration for Micronaut applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Factory
 * public class KubernetesConfig {
 *
 *     @Bean
 *     @Singleton
 *     public KubernetesClient kubernetesClient(KubernetesProperties properties) {
 *         return MicronautKubernetesIntegration.createClient(properties);
 *     }
 * }
 *
 * // Use in service
 * @Singleton
 * public class OrderService {
 *
 *     @Inject
 *     private KubernetesClient kubernetesClient;
 *
 *     public void processOrder() {
 *         PodInfo podInfo = kubernetesClient.getCurrentPodInfo();
 *         log.info("Processing on pod: {}", podInfo.getName());
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class MicronautKubernetesIntegration {

    /**
     * Creates Kubernetes client for Micronaut.
     *
     * @param properties Kubernetes properties
     * @return configured client
     */
    public static KubernetesClient createClient(KubernetesProperties properties) {
        log.info("Creating Kubernetes client for Micronaut");

        if (!properties.isEnabled()) {
            log.warn("Kubernetes integration is disabled");
            return null;
        }

        return new KubernetesClient(properties);
    }

    /**
     * Checks if Micronaut is available.
     *
     * @return true if Micronaut is on classpath
     */
    public static boolean isMicronautAvailable() {
        try {
            Class.forName("io.micronaut.runtime.Micronaut");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

