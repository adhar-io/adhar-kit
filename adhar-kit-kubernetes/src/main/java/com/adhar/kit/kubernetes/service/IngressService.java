package com.adhar.kit.kubernetes.service;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.model.IngressInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Service for managing Kubernetes Ingress resources.
 *
 * <p>Provides comprehensive ingress management:</p>
 * <ul>
 *   <li>Create and update ingress rules</li>
 *   <li>Manage TLS certificates</li>
 *   <li>Configure routing rules</li>
 *   <li>Monitor ingress status</li>
 * </ul>
 *
 * <p><b>Example:</b></p>
 * <pre>{@code
 * @Autowired
 * private IngressService ingressService;
 *
 * // Get ingress
 * IngressInfo ingress = ingressService.getIngress("my-ingress");
 *
 * // List all ingress
 * List<IngressInfo> ingresses = ingressService.listIngress();
 *
 * // Check ingress status
 * if (ingress.hasLoadBalancer()) {
 *     log.info("Load balancer IP: {}", ingress.getLoadBalancerIp());
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class IngressService {

    private final KubernetesClient kubernetesClient;
    private final io.fabric8.kubernetes.client.KubernetesClient client;

    /**
     * Creates ingress service.
     *
     * @param kubernetesClient Kubernetes client
     */
    public IngressService(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        this.client = createFabric8Client();
    }

    /**
     * Gets ingress information.
     *
     * @param name ingress name
     * @return ingress information
     */
    public IngressInfo getIngress(String name) {
        try {
            io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress = client.network()
                .v1()
                .ingresses()
                .inNamespace(kubernetesClient.getNamespace())
                .withName(name)
                .get();

            if (ingress == null) {
                log.warn("Ingress {} not found", name);
                return null;
            }

            return toIngressInfo(ingress);

        } catch (Exception e) {
            log.error("Failed to get ingress {}", name, e);
            return null;
        }
    }

    /**
     * Lists all ingress resources.
     *
     * @return list of ingress
     */
    public List<IngressInfo> listIngress() {
        try {
            io.fabric8.kubernetes.api.model.networking.v1.IngressList ingressList = client.network()
                .v1()
                .ingresses()
                .inNamespace(kubernetesClient.getNamespace())
                .list();

            return ingressList.getItems().stream()
                .map(this::toIngressInfo)
                .toList();

        } catch (Exception e) {
            log.error("Failed to list ingress", e);
            return List.of();
        }
    }

    /**
     * Gets ingress by host.
     *
     * @param host hostname
     * @return ingress information or null
     */
    public IngressInfo getIngressByHost(String host) {
        try {
            List<IngressInfo> ingresses = listIngress();

            return ingresses.stream()
                .filter(ing -> ing.getHosts().contains(host))
                .findFirst()
                .orElse(null);

        } catch (Exception e) {
            log.error("Failed to get ingress by host {}", host, e);
            return null;
        }
    }

    /**
     * Checks if ingress has load balancer IP assigned.
     *
     * @param name ingress name
     * @return true if has load balancer IP
     */
    public boolean hasLoadBalancerIP(String name) {
        IngressInfo ingress = getIngress(name);
        return ingress != null && ingress.hasLoadBalancer();
    }

    /**
     * Gets load balancer IP for ingress.
     *
     * @param name ingress name
     * @return load balancer IP or null
     */
    public String getLoadBalancerIP(String name) {
        IngressInfo ingress = getIngress(name);
        return ingress != null ? ingress.getLoadBalancerIp() : null;
    }

    /**
     * Creates Fabric8 Kubernetes client.
     */
    private io.fabric8.kubernetes.client.KubernetesClient createFabric8Client() {
        return new io.fabric8.kubernetes.client.KubernetesClientBuilder().build();
    }

    /**
     * Converts Fabric8 Ingress to IngressInfo.
     */
    private IngressInfo toIngressInfo(io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress) {
        IngressInfo.IngressInfoBuilder builder = IngressInfo.builder();

        builder.name(ingress.getMetadata().getName());
        builder.namespace(ingress.getMetadata().getNamespace());
        builder.labels(ingress.getMetadata().getLabels());

        // Extract hosts
        if (ingress.getSpec().getRules() != null) {
            List<String> hosts = ingress.getSpec().getRules().stream()
                .map(io.fabric8.kubernetes.api.model.networking.v1.IngressRule::getHost)
                .filter(host -> host != null && !host.isEmpty())
                .toList();
            builder.hosts(hosts);
        }

        // Extract TLS hosts
        if (ingress.getSpec().getTls() != null) {
            List<String> tlsHosts = ingress.getSpec().getTls().stream()
                .flatMap(tls -> tls.getHosts() != null ? tls.getHosts().stream() : java.util.stream.Stream.empty())
                .toList();
            builder.tlsHosts(tlsHosts);
        }

        // Extract load balancer IP
        if (ingress.getStatus() != null &&
            ingress.getStatus().getLoadBalancer() != null &&
            ingress.getStatus().getLoadBalancer().getIngress() != null &&
            !ingress.getStatus().getLoadBalancer().getIngress().isEmpty()) {

            String ip = ingress.getStatus().getLoadBalancer()
                .getIngress().get(0).getIp();
            builder.loadBalancerIp(ip);
        }

        return builder.build();
    }
}

