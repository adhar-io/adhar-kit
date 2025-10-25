package com.adhar.kubernetes.service;

import com.adhar.kubernetes.config.KubernetesProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.KubernetesDiscoveryProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for interacting with Kubernetes API.
 */
@Slf4j
@RequiredArgsConstructor
public class KubernetesService {

    private final KubernetesProperties properties;

    /**
     * Get Kubernetes discovery properties based on the configuration.
     *
     * @return Kubernetes discovery properties
     */
    public KubernetesDiscoveryProperties getDiscoveryProperties() {
        KubernetesDiscoveryProperties.Metadata metadata = new KubernetesDiscoveryProperties.Metadata();
        metadata.setAddLabels(true);
        metadata.setAddAnnotations(true);
        metadata.setAddPorts(true);

        KubernetesDiscoveryProperties discoveryProperties = new KubernetesDiscoveryProperties();
        discoveryProperties.setAllNamespaces(false);
        discoveryProperties.setNamespaces(List.of(properties.getDiscovery().getNamespace()));
        discoveryProperties.setMetadata(metadata);
        
        if (!properties.getDiscovery().getServiceLabels().isEmpty()) {
            Map<String, String> serviceLabels = parseLabels(properties.getDiscovery().getServiceLabels());
            discoveryProperties.setServiceLabels(serviceLabels);
        }
        
        return discoveryProperties;
    }

    /**
     * Parse labels string into a map.
     *
     * @param labelsString comma-separated key=value pairs
     * @return map of labels
     */
    private Map<String, String> parseLabels(String labelsString) {
        Map<String, String> labels = new HashMap<>();
        if (labelsString == null || labelsString.isEmpty()) {
            return labels;
        }

        String[] pairs = labelsString.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.trim().split("=");
            if (keyValue.length == 2) {
                labels.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return labels;
    }

    /**
     * Check if the service is running in a Kubernetes environment.
     *
     * @return true if running in Kubernetes
     */
    public boolean isRunningInKubernetes() {
        return System.getenv("KUBERNETES_SERVICE_HOST") != null && 
               System.getenv("KUBERNETES_SERVICE_PORT") != null;
    }

    /**
     * Get the current namespace.
     *
     * @return current namespace or default if not found
     */
    public String getCurrentNamespace() {
        try {
            return new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Paths.get("/var/run/secrets/kubernetes.io/serviceaccount/namespace")));
        } catch (Exception e) {
            log.debug("Could not determine current namespace", e);
            return "default";
        }
    }
}