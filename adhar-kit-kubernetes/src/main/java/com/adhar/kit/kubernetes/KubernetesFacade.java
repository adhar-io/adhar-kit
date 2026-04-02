package com.adhar.kit.kubernetes;

import com.adhar.kit.kubernetes.api.KubernetesService;
import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.model.DeploymentInfo;
import com.adhar.kit.kubernetes.model.PodInfo;
import com.adhar.kit.kubernetes.model.ServiceInfo;
import com.adhar.kit.kubernetes.service.DeploymentService;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Universal Kubernetes facade for K8s integration.
 *
 * <p>Delegates all operations to {@link KubernetesClient} and {@link DeploymentService}.
 * Supports both Spring DI (constructor injection) and singleton access via {@link #getInstance()}.</p>
 *
 * <p>All methods handle the case where the client is not available (not running in K8s)
 * gracefully by returning safe defaults (empty collections, null values) instead of
 * throwing exceptions.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * KubernetesFacade k8s = KubernetesFacade.getInstance();
 *
 * // Configuration from ConfigMaps
 * @Configuration
 * public class AppConfig {
 *     private final KubernetesFacade k8s = KubernetesFacade.getInstance();
 *
 *     @Bean
 *     public DataSource dataSource() {
 *         if (k8s.isInKubernetes()) {
 *             String url = k8s.getConfigMapValue("app-config", "database.url");
 *             String username = k8s.getConfigMapValue("app-config", "database.username");
 *             String password = k8s.getSecretValue("db-secrets", "password");
 *
 *             return DataSourceBuilder.create()
 *                 .url(url)
 *                 .username(username)
 *                 .password(password)
 *                 .build();
 *         }
 *         return defaultDataSource();
 *     }
 * }
 *
 * // Auto-scaling based on load
 * @Service
 * public class AutoScalingService {
 *     private final KubernetesFacade k8s = KubernetesFacade.getInstance();
 *
 *     @Scheduled(fixedDelay = 60000)
 *     public void checkAndScale() {
 *         if (!k8s.isInKubernetes()) return;
 *
 *         // Get current load metrics
 *         double cpuUsage = getCurrentCpuUsage();
 *
 *         if (cpuUsage > 0.8) {
 *             k8s.scaleDeployment("order-service", 10);
 *         } else if (cpuUsage < 0.3) {
 *             k8s.scaleDeployment("order-service", 3);
 *         }
 *     }
 * }
 *
 * // Pod information
 * Map<String, Object> podInfo = k8s.getCurrentPodInfo();
 * String podName = (String) podInfo.get("name");
 * String namespace = k8s.getCurrentNamespace();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
public class KubernetesFacade implements KubernetesService {

    private static volatile KubernetesFacade instance;

    private volatile KubernetesClient client;
    private volatile DeploymentService deploymentService;
    private final KubernetesProperties properties;

    /**
     * Creates a KubernetesFacade with Spring DI.
     * Use this constructor when the KubernetesClient is managed as a Spring bean.
     *
     * @param client the Kubernetes client
     * @param deploymentService the deployment service
     */
    public KubernetesFacade(KubernetesClient client, DeploymentService deploymentService) {
        this.client = client;
        this.deploymentService = deploymentService;
        this.properties = null;
        log.info("Initialized KubernetesFacade with injected KubernetesClient and DeploymentService");
    }

    /**
     * Creates a KubernetesFacade that will lazily initialize the client
     * using the provided properties.
     *
     * @param properties Kubernetes properties for lazy client creation
     */
    public KubernetesFacade(KubernetesProperties properties) {
        this.properties = properties;
        log.info("Initialized KubernetesFacade with KubernetesProperties (lazy client init)");
    }

    /**
     * Private no-arg constructor for singleton pattern.
     * Client will be lazily initialized with default properties.
     */
    private KubernetesFacade() {
        this.properties = new KubernetesProperties();
        log.info("Initialized KubernetesFacade with default properties (lazy client init)");
    }

    /**
     * Returns the singleton instance, creating it with default properties if needed.
     *
     * @return the singleton KubernetesFacade instance
     */
    public static KubernetesFacade getInstance() {
        if (instance == null) {
            synchronized (KubernetesFacade.class) {
                if (instance == null) {
                    instance = new KubernetesFacade();
                }
            }
        }
        return instance;
    }

    /**
     * Lazily initializes the KubernetesClient if not yet created.
     *
     * @return the KubernetesClient, or null if initialization fails
     */
    private KubernetesClient getClient() {
        if (client == null && properties != null) {
            synchronized (this) {
                if (client == null) {
                    try {
                        client = new KubernetesClient(properties);
                        log.info("Lazily initialized KubernetesClient");
                    } catch (Exception e) {
                        log.warn("Failed to initialize KubernetesClient - " +
                                "Kubernetes operations will return default values", e);
                    }
                }
            }
        }
        return client;
    }

    /**
     * Lazily initializes the DeploymentService if not yet created.
     *
     * @return the DeploymentService, or null if client is unavailable
     */
    private DeploymentService getDeploymentService() {
        if (deploymentService == null) {
            KubernetesClient k8sClient = getClient();
            if (k8sClient != null) {
                synchronized (this) {
                    if (deploymentService == null) {
                        try {
                            deploymentService = new DeploymentService(k8sClient);
                            log.info("Lazily initialized DeploymentService");
                        } catch (Exception e) {
                            log.warn("Failed to initialize DeploymentService", e);
                        }
                    }
                }
            }
        }
        return deploymentService;
    }

    @Override
    public String getConfigMapValue(String configMapName, String key) {
        log.debug("Getting ConfigMap value: {}/{}", configMapName, key);
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            log.warn("KubernetesClient not available, returning null for ConfigMap {}/{}", configMapName, key);
            return null;
        }
        Map<String, String> data = k8sClient.getConfigMap(configMapName);
        return data != null ? data.get(key) : null;
    }

    @Override
    public Map<String, String> getConfigMapData(String configMapName) {
        log.debug("Getting ConfigMap data: {}", configMapName);
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            log.warn("KubernetesClient not available, returning empty map for ConfigMap {}", configMapName);
            return Collections.emptyMap();
        }
        Map<String, String> data = k8sClient.getConfigMap(configMapName);
        return data != null ? data : Collections.emptyMap();
    }

    @Override
    public String getSecretValue(String secretName, String key) {
        log.debug("Getting Secret value: {}/{}", secretName, key);
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            log.warn("KubernetesClient not available, returning null for Secret {}/{}", secretName, key);
            return null;
        }
        Map<String, String> data = k8sClient.getSecret(secretName);
        return data != null ? data.get(key) : null;
    }

    @Override
    public Map<String, String> getSecretData(String secretName) {
        log.debug("Getting Secret data: {}", secretName);
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            log.warn("KubernetesClient not available, returning empty map for Secret {}", secretName);
            return Collections.emptyMap();
        }
        Map<String, String> data = k8sClient.getSecret(secretName);
        return data != null ? data : Collections.emptyMap();
    }

    @Override
    public void scaleDeployment(String deploymentName, int replicas) {
        log.info("Scaling deployment {} to {} replicas", deploymentName, replicas);
        DeploymentService depService = getDeploymentService();
        if (depService == null) {
            log.warn("DeploymentService not available, cannot scale deployment {}", deploymentName);
            return;
        }
        depService.scaleDeployment(deploymentName, replicas);
    }

    @Override
    public Map<String, Object> getCurrentPodInfo() {
        log.debug("Getting current pod info");
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            log.warn("KubernetesClient not available, returning empty pod info");
            return Collections.emptyMap();
        }
        PodInfo podInfo = k8sClient.getCurrentPodInfo();
        return podInfoToMap(podInfo);
    }

    @Override
    public String getCurrentNamespace() {
        log.debug("Getting current namespace");
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            return "default";
        }
        return k8sClient.getNamespace();
    }

    @Override
    public boolean isInKubernetes() {
        return System.getenv("KUBERNETES_SERVICE_HOST") != null;
    }

    @Override
    public List<String> listPods(String labelSelector) {
        log.debug("Listing pods with selector: {}", labelSelector);
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            log.warn("KubernetesClient not available, returning empty pod list");
            return Collections.emptyList();
        }
        List<PodInfo> pods = k8sClient.listPods(labelSelector);
        return pods.stream()
                .map(PodInfo::getName)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // ========================
    // Additional methods
    // ========================

    /**
     * Gets deployment information.
     *
     * @param deploymentName the deployment name
     * @return deployment information, or null if not found or client unavailable
     */
    public DeploymentInfo getDeploymentInfo(String deploymentName) {
        log.debug("Getting deployment info: {}", deploymentName);
        DeploymentService depService = getDeploymentService();
        if (depService == null) {
            log.warn("DeploymentService not available, cannot get deployment info for {}", deploymentName);
            return null;
        }
        return depService.getDeployment(deploymentName);
    }

    /**
     * Lists services matching a label selector.
     *
     * @param labelSelector label selector (e.g., "app=order-service")
     * @return list of service information, or empty list if client unavailable
     */
    public List<ServiceInfo> listServices(String labelSelector) {
        log.debug("Listing services with selector: {}", labelSelector);
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            log.warn("KubernetesClient not available, returning empty service list");
            return Collections.emptyList();
        }
        return k8sClient.discoverServices(labelSelector);
    }

    /**
     * Creates or updates a ConfigMap.
     *
     * @param name ConfigMap name
     * @param data ConfigMap data as key-value pairs
     */
    public void createOrUpdateConfigMap(String name, Map<String, String> data) {
        log.info("Creating/updating ConfigMap: {}", name);
        KubernetesClient k8sClient = getClient();
        if (k8sClient == null) {
            log.warn("KubernetesClient not available, cannot create/update ConfigMap {}", name);
            return;
        }
        k8sClient.createOrUpdateConfigMap(name, data);
    }

    /**
     * Restarts a deployment by triggering a rolling restart.
     *
     * @param deploymentName the deployment name
     * @return true if the restart was initiated successfully
     */
    public boolean restartDeployment(String deploymentName) {
        log.info("Restarting deployment: {}", deploymentName);
        DeploymentService depService = getDeploymentService();
        if (depService == null) {
            log.warn("DeploymentService not available, cannot restart deployment {}", deploymentName);
            return false;
        }
        return depService.restartDeployment(deploymentName);
    }

    /**
     * Converts a PodInfo object to a Map for the KubernetesService interface contract.
     *
     * @param podInfo the pod info to convert
     * @return map representation of the pod info
     */
    private Map<String, Object> podInfoToMap(PodInfo podInfo) {
        if (podInfo == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", podInfo.getName());
        map.put("namespace", podInfo.getNamespace());
        map.put("uid", podInfo.getUid());
        map.put("ip", podInfo.getIp());
        map.put("hostIp", podInfo.getHostIp());
        map.put("nodeName", podInfo.getNodeName());
        map.put("phase", podInfo.getPhase());
        map.put("serviceAccount", podInfo.getServiceAccount());
        map.put("labels", podInfo.getLabels());
        map.put("annotations", podInfo.getAnnotations());
        map.put("isRunning", podInfo.isRunning());
        // Remove null values for cleaner output
        map.values().removeIf(Objects::isNull);
        return map;
    }
}
