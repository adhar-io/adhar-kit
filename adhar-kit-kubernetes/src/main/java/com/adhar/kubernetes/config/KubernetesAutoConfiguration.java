package com.adhar.kubernetes.config;

import com.adhar.kubernetes.health.KubernetesHealthContributor;
import com.adhar.kubernetes.service.KubernetesService;
import org.springframework.boot.actuate.health.HealthContributor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.client.config.KubernetesClientConfigMapPropertySourceLocator;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.config.ConfigMapPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Auto-configuration for Kubernetes integration.
 */
@AutoConfiguration
@EnableConfigurationProperties(KubernetesProperties.class)
@ConditionalOnProperty(prefix = "adhar.kubernetes", name = "enabled", matchIfMissing = true)
public class KubernetesAutoConfiguration {

    /**
     * Configuration for Kubernetes discovery client.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "adhar.kubernetes.discovery", name = "enabled", matchIfMissing = true)
    @ConditionalOnClass(KubernetesDiscoveryClient.class)
    static class KubernetesDiscoveryConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        public KubernetesService kubernetesService(KubernetesProperties properties) {
            return new KubernetesService(properties);
        }
    }
    
    /**
     * Configuration for Kubernetes config client.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "adhar.kubernetes.config", name = "enabled", matchIfMissing = true)
    @ConditionalOnClass(ConfigMapPropertySource.class)
    static class KubernetesConfigConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        public KubernetesClientConfigMapPropertySourceLocator configMapPropertySourceLocator(
                KubernetesProperties properties, Environment environment) {
            return new KubernetesClientConfigMapPropertySourceLocator(environment);
        }
    }
    
    /**
     * Configuration for Kubernetes health indicators.
     */
    @Configuration
    @ConditionalOnProperty(prefix = "adhar.kubernetes.probes", name = "enabled", matchIfMissing = true)
    @ConditionalOnClass(name = "org.springframework.boot.actuate.health.HealthContributor")
    static class KubernetesHealthConfiguration {
        
        @Bean
        @ConditionalOnMissingBean
        public HealthContributor kubernetesHealthContributor(KubernetesProperties properties) {
            return new KubernetesHealthContributor(properties);
        }
    }
}