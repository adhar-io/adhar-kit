package com.adhar.kit.kubernetes.client;

import com.adhar.kit.kubernetes.config.KubernetesProperties;
import com.adhar.kit.kubernetes.model.PodInfo;
import com.adhar.kit.kubernetes.model.ServiceInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Kubernetes client for interacting with Kubernetes API.
 *
 * <p>Provides comprehensive Kubernetes operations:</p>
 * <ul>
 *   <li>Pod information retrieval</li>
 *   <li>Service discovery</li>
 *   <li>ConfigMap and Secret management</li>
 *   <li>Resource creation and deletion</li>
 *   <li>Label and annotation management</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Autowired
 * private KubernetesClient kubernetesClient;
 *
 * // Get current pod info
 * PodInfo podInfo = kubernetesClient.getCurrentPodInfo();
 *
 * // Discover services
 * List<ServiceInfo> services = kubernetesClient.discoverServices("app=order-service");
 *
 * // Get ConfigMap
 * Map<String, String> config = kubernetesClient.getConfigMap("app-config");
 *
 * // Get Secret
 * Map<String, String> secrets = kubernetesClient.getSecret("app-secrets");
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class KubernetesClient {

    private final KubernetesProperties properties;
    private final io.fabric8.kubernetes.client.KubernetesClient client;

    /**
     * Creates Kubernetes client.
     *
     * @param properties Kubernetes properties
     */
    public KubernetesClient(KubernetesProperties properties) {
        this.properties = properties;
        this.client = createClient();
    }

    /**
     * Creates the underlying Kubernetes client.
     *
     * @return Kubernetes client
     */
    private io.fabric8.kubernetes.client.KubernetesClient createClient() {
        try {
            io.fabric8.kubernetes.client.ConfigBuilder configBuilder =
                new io.fabric8.kubernetes.client.ConfigBuilder();

            if (properties.getMasterUrl() != null) {
                configBuilder.withMasterUrl(properties.getMasterUrl());
            }

            if (properties.getNamespace() != null) {
                configBuilder.withNamespace(properties.getNamespace());
            }

            return new io.fabric8.kubernetes.client.KubernetesClientBuilder()
                .withConfig(configBuilder.build())
                .build();

        } catch (Exception e) {
            log.error("Failed to create Kubernetes client", e);
            throw new RuntimeException("Failed to create Kubernetes client", e);
        }
    }

    /**
     * Gets current pod information.
     *
     * @return pod information
     */
    public PodInfo getCurrentPodInfo() {
        try {
            String podName = System.getenv("HOSTNAME");
            if (podName == null) {
                log.warn("HOSTNAME environment variable not set, using default");
                return PodInfo.builder().build();
            }

            io.fabric8.kubernetes.api.model.Pod pod = client.pods()
                .inNamespace(getNamespace())
                .withName(podName)
                .get();

            if (pod == null) {
                log.warn("Pod {} not found", podName);
                return PodInfo.builder().name(podName).build();
            }

            return PodInfo.builder()
                .name(pod.getMetadata().getName())
                .namespace(pod.getMetadata().getNamespace())
                .uid(pod.getMetadata().getUid())
                .ip(pod.getStatus().getPodIP())
                .hostIp(pod.getStatus().getHostIP())
                .nodeName(pod.getSpec().getNodeName())
                .phase(pod.getStatus().getPhase())
                .serviceAccount(pod.getSpec().getServiceAccountName())
                .labels(pod.getMetadata().getLabels())
                .annotations(pod.getMetadata().getAnnotations())
                .build();

        } catch (Exception e) {
            log.error("Failed to get current pod info", e);
            return PodInfo.builder().build();
        }
    }

    /**
     * Discovers services by label selector.
     *
     * @param labelSelector label selector (e.g., "app=order-service")
     * @return list of service information
     */
    public List<ServiceInfo> discoverServices(String labelSelector) {
        try {
            io.fabric8.kubernetes.api.model.ServiceList serviceList = client.services()
                .inNamespace(getNamespace())
                .withLabel(labelSelector)
                .list();

            return serviceList.getItems().stream()
                .map(this::toServiceInfo)
                .toList();

        } catch (Exception e) {
            log.error("Failed to discover services", e);
            return List.of();
        }
    }

    /**
     * Gets ConfigMap data.
     *
     * @param name ConfigMap name
     * @return ConfigMap data
     */
    public Map<String, String> getConfigMap(String name) {
        return getConfigMap(name, getNamespace());
    }

    /**
     * Gets ConfigMap data from specific namespace.
     *
     * @param name ConfigMap name
     * @param namespace namespace
     * @return ConfigMap data
     */
    public Map<String, String> getConfigMap(String name, String namespace) {
        try {
            io.fabric8.kubernetes.api.model.ConfigMap configMap = client.configMaps()
                .inNamespace(namespace)
                .withName(name)
                .get();

            if (configMap == null) {
                log.warn("ConfigMap {} not found in namespace {}", name, namespace);
                return Map.of();
            }

            return configMap.getData();

        } catch (Exception e) {
            log.error("Failed to get ConfigMap {}", name, e);
            return Map.of();
        }
    }

    /**
     * Gets Secret data.
     *
     * @param name Secret name
     * @return Secret data (base64 decoded)
     */
    public Map<String, String> getSecret(String name) {
        return getSecret(name, getNamespace());
    }

    /**
     * Gets Secret data from specific namespace.
     *
     * @param name Secret name
     * @param namespace namespace
     * @return Secret data (base64 decoded)
     */
    public Map<String, String> getSecret(String name, String namespace) {
        try {
            io.fabric8.kubernetes.api.model.Secret secret = client.secrets()
                .inNamespace(namespace)
                .withName(name)
                .get();

            if (secret == null) {
                log.warn("Secret {} not found in namespace {}", name, namespace);
                return Map.of();
            }

            // Decode base64 data
            return secret.getData().entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                    Map.Entry::getKey,
                    e -> new String(java.util.Base64.getDecoder().decode(e.getValue()))
                ));

        } catch (Exception e) {
            log.error("Failed to get Secret {}", name, e);
            return Map.of();
        }
    }

    /**
     * Creates or updates a ConfigMap.
     *
     * @param name ConfigMap name
     * @param data ConfigMap data
     */
    public void createOrUpdateConfigMap(String name, Map<String, String> data) {
        try {
            io.fabric8.kubernetes.api.model.ConfigMap configMap =
                new io.fabric8.kubernetes.api.model.ConfigMapBuilder()
                    .withNewMetadata()
                        .withName(name)
                        .withNamespace(getNamespace())
                    .endMetadata()
                    .withData(data)
                    .build();

            client.configMaps()
                .inNamespace(getNamespace())
                .resource(configMap)
                .createOrReplace();

            log.info("Created/Updated ConfigMap: {}", name);

        } catch (Exception e) {
            log.error("Failed to create/update ConfigMap {}", name, e);
        }
    }

    /**
     * Gets pod by name.
     *
     * @param name pod name
     * @return pod information
     */
    public Optional<PodInfo> getPod(String name) {
        try {
            io.fabric8.kubernetes.api.model.Pod pod = client.pods()
                .inNamespace(getNamespace())
                .withName(name)
                .get();

            if (pod == null) {
                return Optional.empty();
            }

            return Optional.of(toPodInfo(pod));

        } catch (Exception e) {
            log.error("Failed to get pod {}", name, e);
            return Optional.empty();
        }
    }

    /**
     * Lists all pods matching label selector.
     *
     * @param labelSelector label selector
     * @return list of pods
     */
    public List<PodInfo> listPods(String labelSelector) {
        try {
            io.fabric8.kubernetes.api.model.PodList podList = client.pods()
                .inNamespace(getNamespace())
                .withLabel(labelSelector)
                .list();

            return podList.getItems().stream()
                .map(this::toPodInfo)
                .toList();

        } catch (Exception e) {
            log.error("Failed to list pods", e);
            return List.of();
        }
    }

    /**
     * Gets current namespace.
     *
     * @return namespace
     */
    public String getNamespace() {
        return properties.getNamespace();
    }

    /**
     * Closes the client.
     */
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    /**
     * Converts Fabric8 Pod to PodInfo.
     */
    private PodInfo toPodInfo(io.fabric8.kubernetes.api.model.Pod pod) {
        return PodInfo.builder()
            .name(pod.getMetadata().getName())
            .namespace(pod.getMetadata().getNamespace())
            .uid(pod.getMetadata().getUid())
            .ip(pod.getStatus().getPodIP())
            .hostIp(pod.getStatus().getHostIP())
            .nodeName(pod.getSpec().getNodeName())
            .phase(pod.getStatus().getPhase())
            .serviceAccount(pod.getSpec().getServiceAccountName())
            .labels(pod.getMetadata().getLabels())
            .annotations(pod.getMetadata().getAnnotations())
            .build();
    }

    /**
     * Converts Fabric8 Service to ServiceInfo.
     */
    private ServiceInfo toServiceInfo(io.fabric8.kubernetes.api.model.Service service) {
        Map<String, Integer> ports = service.getSpec().getPorts().stream()
            .collect(java.util.stream.Collectors.toMap(
                io.fabric8.kubernetes.api.model.ServicePort::getName,
                io.fabric8.kubernetes.api.model.ServicePort::getPort
            ));

        return ServiceInfo.builder()
            .name(service.getMetadata().getName())
            .namespace(service.getMetadata().getNamespace())
            .clusterIp(service.getSpec().getClusterIP())
            .type(service.getSpec().getType())
            .ports(ports)
            .labels(service.getMetadata().getLabels())
            .build();
    }
}

