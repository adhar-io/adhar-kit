package com.adhar.kubernetes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Kubernetes integration.
 */
@Data
@ConfigurationProperties(prefix = "adhar.kubernetes")
public class KubernetesProperties {
    
    /**
     * Enable or disable Kubernetes integration.
     */
    private boolean enabled = true;
    
    /**
     * Configuration for Kubernetes discovery client.
     */
    private Discovery discovery = new Discovery();
    
    /**
     * Configuration for Kubernetes config client.
     */
    private Config config = new Config();
    
    /**
     * Configuration for Kubernetes probes.
     */
    private Probes probes = new Probes();
    
    /**
     * Discovery client configuration.
     */
    @Data
    public static class Discovery {
        /**
         * Enable or disable Kubernetes discovery client.
         */
        private boolean enabled = true;
        
        /**
         * Namespace to discover services from.
         */
        private String namespace = "default";
        
        /**
         * Service labels to filter by.
         */
        private String serviceLabels = "";
    }
    
    /**
     * Config client configuration.
     */
    @Data
    public static class Config {
        /**
         * Enable or disable Kubernetes config client.
         */
        private boolean enabled = true;
        
        /**
         * Namespace to get config maps from.
         */
        private String namespace = "default";
        
        /**
         * Config map name.
         */
        private String name = "application-config";
        
        /**
         * Sources to include.
         */
        private String sources = "configmap,secrets";
    }
    
    /**
     * Kubernetes probes configuration.
     */
    @Data
    public static class Probes {
        /**
         * Enable or disable Kubernetes probes.
         */
        private boolean enabled = true;
        
        /**
         * Liveness probe path.
         */
        private String livenessPath = "/actuator/health/liveness";
        
        /**
         * Readiness probe path.
         */
        private String readinessPath = "/actuator/health/readiness";
        
        /**
         * Startup probe path.
         */
        private String startupPath = "/actuator/health/startup";
    }
}