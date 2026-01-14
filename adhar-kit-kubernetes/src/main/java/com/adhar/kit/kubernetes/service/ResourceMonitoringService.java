package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.PodInfo;
import com.adhar.kit.kubernetes.model.ResourceMetrics;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for monitoring Kubernetes resources.
 *
 * <p>Provides comprehensive resource monitoring:</p>
 * <ul>
 *   <li>CPU and memory usage monitoring</li>
 *   <li>Pod resource metrics</li>
 *   <li>Node resource metrics</li>
 *   <li>Namespace resource quotas</li>
 *   <li>Resource alerts and thresholds</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Autowired
 * private ResourceMonitoringService monitoringService;
 *
 * // Get pod metrics
 * ResourceMetrics metrics = monitoringService.getPodMetrics("my-pod");
 *
 * // Check if pod is using too much CPU
 * if (metrics.getCpuUsagePercentage() > 80) {
 *     log.warn("High CPU usage: {}%", metrics.getCpuUsagePercentage());
 * }
 *
 * // Get namespace resource summary
 * Map<String, ResourceMetrics> summary = monitoringService.getNamespaceResourceSummary();
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class ResourceMonitoringService {

    private final KubernetesClient kubernetesClient;
    private final io.fabric8.kubernetes.client.KubernetesClient client;

    /**
     * Creates resource monitoring service.
     *
     * @param kubernetesClient Kubernetes client
     */
    public ResourceMonitoringService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.client = createFabric8Client();
    }

    /**
     * Gets resource metrics for a pod.
     *
     * @param podName pod name
     * @return resource metrics
     */
    public ResourceMetrics getPodMetrics(String podName) {
        try {
            // Get pod
            io.fabric8.kubernetes.api.model.Pod pod = client.pods()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(podName)
                .get();

            if (pod == null) {
                log.warn("Pod {} not found", podName);
                return ResourceMetrics.builder().build();
            }

            // Get metrics from pod status (simplified - in production use metrics server)
            return extractPodMetrics(pod);

        } catch (Exception e) {
            log.error("Failed to get pod metrics for {}", podName, e);
            return ResourceMetrics.builder().build();
        }
    }

    /**
     * Gets resource metrics for all pods in namespace.
     *
     * @return map of pod name to metrics
     */
    public Map<String, ResourceMetrics> getAllPodMetrics() {
        Map<String, ResourceMetrics> metricsMap = new HashMap<>();

        try {
            List<PodInfo> pods = kubernetesClient.listPods("");

            for (PodInfo pod : pods) {
                ResourceMetrics metrics = getPodMetrics(pod.getName());
                metricsMap.put(pod.getName(), metrics);
            }

        } catch (Exception e) {
            log.error("Failed to get all pod metrics", e);
        }

        return metricsMap;
    }

    /**
     * Gets namespace resource summary.
     *
     * @return resource summary
     */
    public Map<String, ResourceMetrics> getNamespaceResourceSummary() {
        Map<String, ResourceMetrics> summary = new HashMap<>();

        try {
            // Get all pods in namespace
            Map<String, ResourceMetrics> podMetrics = getAllPodMetrics();

            // Aggregate metrics
            ResourceMetrics.ResourceMetricsBuilder totalBuilder = ResourceMetrics.builder();
            long totalCpu = 0;
            long totalMemory = 0;

            for (ResourceMetrics metrics : podMetrics.values()) {
                totalCpu += metrics.getCpuUsageMillicores();
                totalMemory += metrics.getMemoryUsageBytes();
            }

            totalBuilder.cpuUsageMillicores(totalCpu);
            totalBuilder.memoryUsageBytes(totalMemory);

            summary.put("total", totalBuilder.build());
            summary.putAll(podMetrics);

        } catch (Exception e) {
            log.error("Failed to get namespace resource summary", e);
        }

        return summary;
    }

    /**
     * Checks if pod exceeds resource thresholds.
     *
     * @param podName pod name
     * @param cpuThresholdPercent CPU threshold percentage
     * @param memoryThresholdPercent memory threshold percentage
     * @return true if exceeds thresholds
     */
    public boolean exceedsResourceThresholds(String podName,
                                             int cpuThresholdPercent,
                                             int memoryThresholdPercent) {
        ResourceMetrics metrics = getPodMetrics(podName);

        return metrics.getCpuUsagePercentage() > cpuThresholdPercent ||
               metrics.getMemoryUsagePercentage() > memoryThresholdPercent;
    }

    /**
     * Gets pods exceeding resource thresholds.
     *
     * @param cpuThreshold CPU threshold percentage
     * @param memoryThreshold memory threshold percentage
     * @return list of pod names
     */
    public List<String> getPodsExceedingThresholds(int cpuThreshold, int memoryThreshold) {
        Map<String, ResourceMetrics> allMetrics = getAllPodMetrics();

        return allMetrics.entrySet().stream()
            .filter(entry -> {
                ResourceMetrics metrics = entry.getValue();
                return metrics.getCpuUsagePercentage() > cpuThreshold ||
                       metrics.getMemoryUsagePercentage() > memoryThreshold;
            })
            .map(Map.Entry::getKey)
            .toList();
    }

    /**
     * Gets resource quota for namespace.
     *
     * @return resource quota info
     */
    public Map<String, String> getResourceQuota() {
        Map<String, String> quota = new HashMap<>();

        try {
            io.fabric8.kubernetes.api.model.ResourceQuotaList quotaList = client.resourceQuotas()
                .inNamespace(kubernetesClient.getNamespace())
                .list();

            if (!quotaList.getItems().isEmpty()) {
                io.fabric8.kubernetes.api.model.ResourceQuota resourceQuota =
                    quotaList.getItems().get(0);

                Map<String, io.fabric8.kubernetes.api.model.Quantity> hard =
                    resourceQuota.getStatus().getHard();
                Map<String, io.fabric8.kubernetes.api.model.Quantity> used =
                    resourceQuota.getStatus().getUsed();

                if (hard != null) {
                    hard.forEach((key, value) ->
                        quota.put("hard." + key, value.getAmount()));
                }

                if (used != null) {
                    used.forEach((key, value) ->
                        quota.put("used." + key, value.getAmount()));
                }
            }

        } catch (Exception e) {
            log.error("Failed to get resource quota", e);
        }

        return quota;
    }

    /**
     * Extracts metrics from pod (simplified).
     */
    private ResourceMetrics extractPodMetrics(io.fabric8.kubernetes.api.model.Pod pod) {
        ResourceMetrics.ResourceMetricsBuilder builder = ResourceMetrics.builder();

        builder.podName(pod.getMetadata().getName());
        builder.namespace(pod.getMetadata().getNamespace());

        // In production, use Kubernetes Metrics Server API
        // This is a simplified version using resource requests/limits
        var containers = pod.getSpec().getContainers();

        long totalCpuRequest = 0;
        long totalMemoryRequest = 0;

        for (var container : containers) {
            if (container.getResources() != null &&
                container.getResources().getRequests() != null) {

                var requests = container.getResources().getRequests();

                if (requests.get("cpu") != null) {
                    totalCpuRequest += parseCpuToMillicores(requests.get("cpu").getAmount());
                }

                if (requests.get("memory") != null) {
                    totalMemoryRequest += parseMemoryToBytes(requests.get("memory").getAmount());
                }
            }
        }

        builder.cpuRequestMillicores(totalCpuRequest);
        builder.memoryRequestBytes(totalMemoryRequest);

        // Set usage to request for now (in production, get from metrics server)
        builder.cpuUsageMillicores(totalCpuRequest);
        builder.memoryUsageBytes(totalMemoryRequest);

        return builder.build();
    }

    /**
     * Parses CPU to millicores.
     */
    private long parseCpuToMillicores(String cpu) {
        if (cpu.endsWith("m")) {
            return Long.parseLong(cpu.substring(0, cpu.length() - 1));
        } else {
            return Long.parseLong(cpu) * 1000;
        }
    }

    /**
     * Parses memory to bytes.
     */
    private long parseMemoryToBytes(String memory) {
        if (memory.endsWith("Ki")) {
            return Long.parseLong(memory.substring(0, memory.length() - 2)) * 1024;
        } else if (memory.endsWith("Mi")) {
            return Long.parseLong(memory.substring(0, memory.length() - 2)) * 1024 * 1024;
        } else if (memory.endsWith("Gi")) {
            return Long.parseLong(memory.substring(0, memory.length() - 2)) * 1024 * 1024 * 1024;
        } else {
            return Long.parseLong(memory);
        }
    }

    /**
     * Creates Fabric8 Kubernetes client.
     */
    private io.fabric8.kubernetes.client.KubernetesClient createFabric8Client() {
        return new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
    }
}

