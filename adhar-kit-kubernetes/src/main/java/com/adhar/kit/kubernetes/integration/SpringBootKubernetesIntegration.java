package com.adhar.kit.kubernetes.integration;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring Boot integration for Adhar Kubernetes.
 *
 * <p>Provides automatic Kubernetes configuration for Spring Boot applications.</p>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * @Configuration
 * public class KubernetesConfig {
 *
 *     @Bean
 *     public KubernetesClient kubernetesClient(KubernetesProperties properties) {
 *         return SpringBootKubernetesIntegration.createClient(properties);
 *     }
 *
 *     @Bean
 *     public PodInfo currentPodInfo(KubernetesClient client) {
 *         return client.getCurrentPodInfo();
 *     }
 * }
 *
 * // Use in service
 * @Service
 * public class OrderService {
 *
 *     @Autowired
 *     private KubernetesClient kubernetesClient;
 *
 *     @Autowired
 *     private PodInfo podInfo;
 *
 *     public void processOrder() {
 *         log.info("Processing on pod: {}", podInfo.getName());
 *
 *         // Discover other services
 *         List<ServiceInfo> services = kubernetesClient
 *             .discoverServices("app=payment-service");
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class SpringBootKubernetesIntegration {

    /**
     * Creates Kubernetes client for Spring Boot.
     *
     * @param properties Kubernetes properties
     * @return configured client
     */
    public static KubernetesClient createClient(KubernetesProperties properties) {
        log.info("Creating Kubernetes client for Spring Boot");

        if (!properties.isEnabled()) {
            log.warn("Kubernetes integration is disabled");
            return null;
        }

        return new KubernetesClient(properties);
    }

    /**
     * Checks if Spring Boot is available.
     *
     * @return true if Spring Boot is on classpath
     */
    public static boolean isSpringBootAvailable() {
        try {
            Class.forName("org.springframework.boot.SpringApplication");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}

