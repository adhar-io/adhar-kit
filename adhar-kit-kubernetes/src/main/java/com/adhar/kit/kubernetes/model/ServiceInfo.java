package com.adhar.kit.kubernetes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service discovery information model.
 *
 * <p>Contains information about discovered services in Kubernetes.</p>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * List<ServiceInfo> services = kubernetesClient.discoverServices("app=order-service");
 * for (ServiceInfo service : services) {
 *     String name = service.getName();
 *     List<ServiceEndpoint> endpoints = service.getEndpoints();
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceInfo {

    /**
     * Service name.
     */
    private String name;

    /**
     * Service namespace.
     */
    private String namespace;

    /**
     * Cluster IP.
     */
    private String clusterIp;

    /**
     * Service type (ClusterIP, NodePort, LoadBalancer).
     */
    private String type;

    /**
     * Service ports.
     */
    @Builder.Default
    private Map<String, Integer> ports = new HashMap<>();

    /**
     * Service labels.
     */
    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    /**
     * Service endpoints.
     */
    @Builder.Default
    private List<ServiceEndpoint> endpoints = List.of();

    /**
     * Service endpoint model.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceEndpoint {
        private String ip;
        private Integer port;
        private String nodeName;
        private boolean ready;
    }
}

