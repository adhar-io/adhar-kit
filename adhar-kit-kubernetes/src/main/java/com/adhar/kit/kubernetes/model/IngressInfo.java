package com.adhar.kit.kubernetes.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ingress information model.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IngressInfo {

    private String name;
    private String namespace;

    @Builder.Default
    private Map<String, String> labels = new HashMap<>();

    @Builder.Default
    private List<String> hosts = List.of();

    @Builder.Default
    private List<String> tlsHosts = List.of();

    private String loadBalancerIp;

    /**
     * Checks if ingress has load balancer IP.
     *
     * @return true if has IP
     */
    public boolean hasLoadBalancer() {
        return loadBalancerIp != null && !loadBalancerIp.isEmpty();
    }

    /**
     * Checks if ingress has TLS configured.
     *
     * @return true if has TLS
     */
    public boolean hasTLS() {
        return tlsHosts != null && !tlsHosts.isEmpty();
    }
}

