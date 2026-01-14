package com.adhar.kit.kubernetes.api;

import java.util.Map;

/**
 * Framework-agnostic Kubernetes Service API.
 *
 * <p>This interface provides a unified Kubernetes client abstraction across
 * Spring Boot, Quarkus, and Micronaut frameworks.</p>
 *
 * <p><b>Supported Features:</b></p>
 * <ul>
 *   <li>Pod management</li>
 *   <li>Service discovery</li>
 *   <li>ConfigMap and Secret access</li>
 *   <li>Deployment scaling</li>
 *   <li>Health checks integration</li>
 * </ul>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * KubernetesService k8s = KubernetesFacade.getInstance();
 *
 * // Get config from ConfigMap
 * String config = k8s.getConfigMapValue("app-config", "database.url");
 *
 * // Get secret
 * String password = k8s.getSecretValue("db-secrets", "password");
 *
 * // Scale deployment
 * k8s.scaleDeployment("order-service", 5);
 *
 * // Get pod info
 * Map<String, Object> podInfo = k8s.getCurrentPodInfo();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 * @see com.adhar.kit.kubernetes.KubernetesFacade
 */
public interface KubernetesService {

    /**
     * Gets a value from a ConfigMap.
     *
     * @param configMapName the ConfigMap name
     * @param key the data key
     * @return the value
     */
    String getConfigMapValue(String configMapName, String key);

    /**
     * Gets all data from a ConfigMap.
     *
     * @param configMapName the ConfigMap name
     * @return map of all key-value pairs
     */
    Map<String, String> getConfigMapData(String configMapName);

    /**
     * Gets a value from a Secret.
     *
     * @param secretName the Secret name
     * @param key the data key
     * @return the decoded value
     */
    String getSecretValue(String secretName, String key);

    /**
     * Gets all data from a Secret.
     *
     * @param secretName the Secret name
     * @return map of all key-value pairs (decoded)
     */
    Map<String, String> getSecretData(String secretName);

    /**
     * Scales a deployment.
     *
     * @param deploymentName the deployment name
     * @param replicas desired replica count
     */
    void scaleDeployment(String deploymentName, int replicas);

    /**
     * Gets current pod information.
     *
     * @return pod metadata and status
     */
    Map<String, Object> getCurrentPodInfo();

    /**
     * Gets the current namespace.
     *
     * @return namespace name
     */
    String getCurrentNamespace();

    /**
     * Checks if running in Kubernetes.
     *
     * @return true if in K8s, false otherwise
     */
    boolean isInKubernetes();

    /**
     * Lists pods by label selector.
     *
     * @param labelSelector label selector (e.g., "app=order-service")
     * @return list of pod names
     */
    java.util.List<String> listPods(String labelSelector);
}

