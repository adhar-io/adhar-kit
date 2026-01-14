package com.adhar.kit.kubernetes.integration;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Quarkus integration for Adhar Kubernetes.
 *
 * <p>Provides automatic Kubernetes configuration for Quarkus applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @ApplicationScoped
 * public class KubernetesConfig {
 *
 *     @Produces
 *     @Singleton
 *     public KubernetesClient kubernetesClient(@ConfigProperty KubernetesProperties properties) {
 *         return QuarkusKubernetesIntegration.createClient(properties);
 *     }
 * }
 *
 * // Use in service
 * @ApplicationScoped
 * public class OrderService {
 *
 *     @Inject
 *     KubernetesClient kubernetesClient;
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
public class QuarkusKubernetesIntegration {

    /**
     * Creates Kubernetes client for Quarkus.
     *
     * @param properties Kubernetes properties
     * @return configured client
     */
    public static KubernetesClient createClient(KubernetesProperties properties) {
        log.info("Creating Kubernetes client for Quarkus");

        if (!properties.isEnabled()) {
            log.warn("Kubernetes integration is disabled");
            return null;
        }

        return new KubernetesClient(properties);
    }

    /**
     * Checks if Quarkus is available.
     *
     * @return true if Quarkus is on classpath
     */
    public static boolean isQuarkusAvailable() {
        try {
            Class.forName("io.quarkus.runtime.Quarkus");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

