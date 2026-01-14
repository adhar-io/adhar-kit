package com.adhar.kit.kubernetes.config;

import lombok.Data;

/**
 * Configuration properties for Adhar Kubernetes module.
 *
 * <p>Provides comprehensive Kubernetes configuration for enterprise microservices:</p>
 * <ul>
 *   <li>Kubernetes client configuration</li>
 *   <li>Service discovery</li>
 *   <li>ConfigMap and Secret management</li>
 *   <li>Pod information access</li>
 *   <li>Leader election</li>
 *   <li>Resource management</li>
 * </ul>
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   kubernetes:
 *     enabled: true
 *     namespace: default
 *     discovery:
 *       enabled: true
 *       all-namespaces: false
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
public class KubernetesProperties {

    /**
     * Enable Kubernetes integration.
     */
    private boolean enabled = true;

    /**
     * Kubernetes namespace.
     */
    private String namespace = "default";

    /**
     * Master URL (optional, auto-detected if not set).
     */
    private String masterUrl;

    /**
     * API version.
     */
    private String apiVersion = "v1";

    /**
     * Service discovery configuration.
     */
    private DiscoveryConfig discovery = new DiscoveryConfig();

    /**
     * ConfigMap configuration.
     */
    private ConfigMapConfig configMap = new ConfigMapConfig();

    /**
     * Secret configuration.
     */
    private SecretConfig secret = new SecretConfig();

    /**
     * Leader election configuration.
     */
    private LeaderElectionConfig leaderElection = new LeaderElectionConfig();

    /**
     * Pod information configuration.
     */
    private PodConfig pod = new PodConfig();

    /**
     * Service discovery configuration.
     */
    @Data
    public static class DiscoveryConfig {
        private boolean enabled = true;
        private boolean allNamespaces = false;
        private String serviceLabel = "app";
        private long cacheRefreshInterval = 30000; // 30 seconds
    }

    /**
     * ConfigMap configuration.
     */
    @Data
    public static class ConfigMapConfig {
        private boolean enabled = true;
        private String name;
        private boolean watchEnabled = true;
        private long watchInterval = 10000; // 10 seconds
    }

    /**
     * Secret configuration.
     */
    @Data
    public static class SecretConfig {
        private boolean enabled = true;
        private String name;
        private boolean watchEnabled = true;
        private long watchInterval = 10000;
    }

    /**
     * Leader election configuration.
     */
    @Data
    public static class LeaderElectionConfig {
        private boolean enabled = false;
        private String lockName = "leader-election";
        private long leaseDuration = 15000; // 15 seconds
        private long renewDeadline = 10000; // 10 seconds
        private long retryPeriod = 2000; // 2 seconds
    }

    /**
     * Pod information configuration.
     */
    @Data
    public static class PodConfig {
        private boolean enabled = true;
        private String podName;
        private String podIp;
        private String nodeName;
    }
}

