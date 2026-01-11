package com.adhar.kit.metrics.util;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Utility class for Kubernetes-specific metrics.
 * <p>
 * This class provides methods for recording metrics related to Kubernetes resources,
 * making it easier for developers to monitor their applications in Kubernetes environments.
 * </p>
 */
@Slf4j
public class KubernetesMetricsUtils {

    private final MeterRegistry registry;
    private final AdharMetricsProperties properties;

    // Cache for Kubernetes metadata to avoid frequent lookups
    private final Map<String, String> metadataCache = new HashMap<>();
    
    // Flag to indicate if running in Kubernetes
    private final AtomicReference<Boolean> isInKubernetes = new AtomicReference<>(null);

    // Kubernetes service account token path
    private static final String SERVICE_ACCOUNT_TOKEN_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/token";
    private static final String SERVICE_ACCOUNT_NAMESPACE_PATH = "/var/run/secrets/kubernetes.io/serviceaccount/namespace";

    // Environment variables commonly set in Kubernetes
    private static final String POD_NAME_ENV = "HOSTNAME";
    private static final String POD_NAMESPACE_ENV = "POD_NAMESPACE";
    private static final String NODE_NAME_ENV = "NODE_NAME";

    /**
     * Constructor for KubernetesMetricsUtils.
     *
     * @param registry The meter registry
     * @param properties The metrics properties
     */
    public KubernetesMetricsUtils(MeterRegistry registry, AdharMetricsProperties properties) {
        this.registry = registry;
        this.properties = properties;

        if (isRunningInKubernetes()) {
            initializeKubernetesMetadata();
        }
    }

    /**
     * Checks if the application is running in a Kubernetes environment.
     *
     * @return true if running in Kubernetes, false otherwise
     */
    public boolean isRunningInKubernetes() {
        Boolean cached = isInKubernetes.get();
        if (cached != null) {
            return cached;
        }

        boolean inK8s = Files.exists(Paths.get(SERVICE_ACCOUNT_TOKEN_PATH)) ||
                        System.getenv("KUBERNETES_SERVICE_HOST") != null ||
                        System.getenv("KUBERNETES_SERVICE_PORT") != null;

        isInKubernetes.set(inK8s);
        log.debug("Kubernetes environment detection: {}", inK8s);
        return inK8s;
    }

    /**
     * Gets the current pod name.
     *
     * @return The pod name or null if not available
     */
    public String getPodName() {
        return metadataCache.computeIfAbsent("podName", k -> {
            String podName = System.getenv(POD_NAME_ENV);
            if (StringUtils.hasText(podName)) {
                return podName;
            }

            // Fallback to hostname
            return System.getenv("HOSTNAME");
        });
    }

    /**
     * Gets the current namespace.
     *
     * @return The namespace or null if not available
     */
    public String getNamespace() {
        return metadataCache.computeIfAbsent("namespace", k -> {
            String namespace = System.getenv(POD_NAMESPACE_ENV);
            if (StringUtils.hasText(namespace)) {
                return namespace;
            }

            // Try to read from service account
            try {
                Path namespacePath = Paths.get(SERVICE_ACCOUNT_NAMESPACE_PATH);
                if (Files.exists(namespacePath)) {
                    return Files.readString(namespacePath).trim();
                }
            } catch (IOException e) {
                log.debug("Failed to read namespace from service account: {}", e.getMessage());
            }

            return "default";
        });
    }

    /**
     * Gets the current node name.
     *
     * @return The node name or null if not available
     */
    public String getNodeName() {
        return metadataCache.computeIfAbsent("nodeName", k -> System.getenv(NODE_NAME_ENV));
    }

    /**
     * Creates Kubernetes-specific tags for metrics.
     *
     * @return List of Kubernetes tags
     */
    public List<Tag> createKubernetesTags() {
        List<Tag> tags = new ArrayList<>();

        if (properties.getKubernetes().isIncludePodInfo()) {
            String podName = getPodName();
            if (StringUtils.hasText(podName)) {
                tags.add(Tag.of("pod", podName));
            }
        }

        if (properties.getKubernetes().isIncludeNamespace()) {
            String namespace = getNamespace();
            if (StringUtils.hasText(namespace)) {
                tags.add(Tag.of("namespace", namespace));
            }
        }
        
        if (properties.getKubernetes().isIncludeNodeInfo()) {
            String nodeName = getNodeName();
            if (StringUtils.hasText(nodeName)) {
                tags.add(Tag.of("node", nodeName));
            }
        }

        return tags;
    }

    /**
     * Creates tags array from Kubernetes metadata.
     *
     * @return Tags array for use with metrics
     */
    public String[] createKubernetesTagsArray() {
        List<Tag> tags = createKubernetesTags();
        String[] tagArray = new String[tags.size() * 2];

        for (int i = 0; i < tags.size(); i++) {
            Tag tag = tags.get(i);
            tagArray[i * 2] = tag.getKey();
            tagArray[i * 2 + 1] = tag.getValue();
        }

        return tagArray;
    }

    /**
     * Records a Kubernetes deployment metric.
     *
     * @param deploymentName The deployment name
     * @param replicas The number of replicas
     * @param availableReplicas The number of available replicas
     */
    public void recordDeploymentMetrics(String deploymentName, int replicas, int availableReplicas) {
        if (!isRunningInKubernetes() || !StringUtils.hasText(deploymentName)) {
            return;
        }
        
        String[] tags = createKubernetesTagsArray();
        String[] deploymentTags = addTag(tags, "deployment", deploymentName);

        registry.gauge("kubernetes.deployment.replicas", Tags.of(deploymentTags), replicas);
        registry.gauge("kubernetes.deployment.available_replicas", Tags.of(deploymentTags), availableReplicas);
        registry.gauge("kubernetes.deployment.unavailable_replicas", Tags.of(deploymentTags),
                      Math.max(0, replicas - availableReplicas));
    }

    /**
     * Records a Kubernetes service metric.
     *
     * @param serviceName The service name
     * @param endpointCount The number of endpoints
     */
    public void recordServiceMetrics(String serviceName, int endpointCount) {
        if (!isRunningInKubernetes() || !StringUtils.hasText(serviceName)) {
            return;
        }
        
        String[] tags = createKubernetesTagsArray();
        String[] serviceTags = addTag(tags, "service", serviceName);

        registry.gauge("kubernetes.service.endpoints", Tags.of(serviceTags), endpointCount);
    }

    /**
     * Records a Kubernetes pod restart metric.
     *
     * @param reason The restart reason
     */
    public void recordPodRestart(String reason) {
        if (!isRunningInKubernetes()) {
            return;
        }
        
        String[] tags = createKubernetesTagsArray();
        String[] restartTags = StringUtils.hasText(reason) ? addTag(tags, "reason", reason) : tags;

        registry.counter("kubernetes.pod.restarts", restartTags).increment();
    }

    /**
     * Records a Kubernetes resource quota metric.
     *
     * @param resourceName The resource name (cpu, memory, etc.)
     * @param used The used amount
     * @param limit The limit amount
     */
    public void recordResourceQuota(String resourceName, double used, double limit) {
        if (!isRunningInKubernetes() || !StringUtils.hasText(resourceName)) {
            return;
        }
        
        String[] tags = createKubernetesTagsArray();
        String[] resourceTags = addTag(tags, "resource", resourceName);

        registry.gauge("kubernetes.resource.used", Tags.of(resourceTags), used);
        registry.gauge("kubernetes.resource.limit", Tags.of(resourceTags), limit);

        if (limit > 0) {
            double utilization = (used / limit) * 100;
            registry.gauge("kubernetes.resource.utilization_percent", Tags.of(resourceTags), utilization);
        }
    }

    /**
     * Records a Kubernetes ingress metric.
     *
     * @param ingressName The ingress name
     * @param hostCount The number of hosts
     * @param pathCount The number of paths
     */
    public void recordIngressMetrics(String ingressName, int hostCount, int pathCount) {
        if (!isRunningInKubernetes() || !StringUtils.hasText(ingressName)) {
            return;
        }

        String[] tags = createKubernetesTagsArray();
        String[] ingressTags = addTag(tags, "ingress", ingressName);

        registry.gauge("kubernetes.ingress.hosts", Tags.of(ingressTags), hostCount);
        registry.gauge("kubernetes.ingress.paths", Tags.of(ingressTags), pathCount);
    }

    /**
     * Gets all cached Kubernetes metadata.
     *
     * @return Map of metadata key-value pairs
     */
    public Map<String, String> getKubernetesMetadata() {
        Map<String, String> metadata = new HashMap<>(metadataCache);

        // Add current values
        metadata.put("podName", getPodName());
        metadata.put("namespace", getNamespace());
        metadata.put("nodeName", getNodeName());
        metadata.put("inKubernetes", String.valueOf(isRunningInKubernetes()));

        return metadata;
    }

    /**
     * Clears the metadata cache.
     */
    public void clearCache() {
        metadataCache.clear();
        isInKubernetes.set(null);
        log.debug("Kubernetes metadata cache cleared");
    }

    /**
     * Initializes Kubernetes metadata by performing initial lookups.
     */
    private void initializeKubernetesMetadata() {
        log.info("Initializing Kubernetes metadata for pod: {}, namespace: {}, node: {}",
                getPodName(), getNamespace(), getNodeName());
    }

    /**
     * Adds a tag to an existing tags array.
     *
     * @param existingTags The existing tags
     * @param key The tag key
     * @param value The tag value
     * @return New tags array with the added tag
     */
    private String[] addTag(String[] existingTags, String key, String value) {
        String[] newTags = new String[existingTags.length + 2];
        System.arraycopy(existingTags, 0, newTags, 0, existingTags.length);
        newTags[existingTags.length] = key;
        newTags[existingTags.length + 1] = value;
        return newTags;
    }
}