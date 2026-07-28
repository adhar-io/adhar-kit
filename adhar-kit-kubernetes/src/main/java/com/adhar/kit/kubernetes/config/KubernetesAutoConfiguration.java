package com.adhar.kit.kubernetes.config;

import com.adhar.kit.kubernetes.client.KubernetesClient;
import com.adhar.kit.kubernetes.discovery.CachedServiceDiscovery;
import com.adhar.kit.kubernetes.health.KubernetesLivenessHealthIndicator;
import com.adhar.kit.kubernetes.health.KubernetesReadinessHealthIndicator;
import com.adhar.kit.kubernetes.lifecycle.GracefulShutdownHandler;
import com.adhar.kit.kubernetes.service.LeaderElectionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Auto-configuration for the Adhar Kit Kubernetes module.
 *
 * <p>Wires the pieces that are safe to create in any environment (including
 * outside a cluster):</p>
 * <ul>
 *   <li>{@link GracefulShutdownHandler} - the readiness gate / pre-stop drain
 *       coordinator (property {@code adhar.kubernetes.graceful-shutdown.enabled}).</li>
 *   <li>{@link CachedServiceDiscovery} - a TTL cache in front of
 *       {@link KubernetesClient#discoverServices(String)}, created only when a
 *       {@link KubernetesClient} bean is present
 *       (property {@code adhar.kubernetes.discovery.cache-enabled}).</li>
 *   <li>Kubernetes readiness/liveness {@link HealthIndicator} beans, contributed to
 *       the actuator health endpoint - only when Spring Boot Actuator is on the
 *       classpath (property {@code adhar.kubernetes.probes.enabled}). Add them to
 *       the probe groups via
 *       {@code management.endpoint.health.group.readiness.include=readinessState,kubernetesReadiness}
 *       and
 *       {@code management.endpoint.health.group.liveness.include=livenessState,kubernetesLiveness}.</li>
 * </ul>
 *
 * <p>The Fabric8-backed {@link KubernetesClient} itself is intentionally not
 * auto-created here: constructing it eagerly can fail outside a cluster, so it is
 * expected to be supplied by the application (or another configuration) when live
 * API access is required. All beans use {@link ConditionalOnMissingBean} so an
 * application may override any of them.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(KubernetesProperties.class)
@ConditionalOnProperty(prefix = "adhar.kubernetes", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KubernetesAutoConfiguration {

    /**
     * Readiness gate / graceful-shutdown handler exposing {@code isReady()}.
     *
     * @param properties module properties
     * @return the handler
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.kubernetes.graceful-shutdown", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public GracefulShutdownHandler gracefulShutdownHandler(KubernetesProperties properties) {
        long drainSeconds = properties.getGracefulShutdown().getPreStopDrainSeconds();
        log.info("Configuring Kubernetes graceful-shutdown handler (drain={}s)", drainSeconds);
        return new GracefulShutdownHandler(Duration.ofSeconds(drainSeconds));
    }

    /**
     * TTL cache in front of live service discovery. Created only when a
     * {@link KubernetesClient} bean is available.
     *
     * @param client     the underlying client
     * @param properties module properties
     * @return the cached discovery decorator
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(KubernetesClient.class)
    @ConditionalOnProperty(prefix = "adhar.kubernetes.discovery", name = "cache-enabled",
            havingValue = "true", matchIfMissing = true)
    public CachedServiceDiscovery cachedServiceDiscovery(KubernetesClient client, KubernetesProperties properties) {
        long ttlMillis = properties.getDiscovery().getCacheRefreshInterval();
        log.info("Configuring cached Kubernetes service discovery (ttl={}ms)", ttlMillis);
        return new CachedServiceDiscovery(client, Duration.ofMillis(ttlMillis));
    }

    /**
     * Registers Kubernetes readiness/liveness health indicators. Gated on Spring
     * Boot Actuator's {@link HealthIndicator} being on the classpath so the module
     * remains usable without actuator.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnProperty(prefix = "adhar.kubernetes.probes", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    static class KubernetesProbeHealthConfiguration {

        /**
         * Readiness indicator surfacing {@link GracefulShutdownHandler#isReady()}.
         */
        @Bean
        @ConditionalOnMissingBean
        public KubernetesReadinessHealthIndicator kubernetesReadinessHealthIndicator(
                ObjectProvider<GracefulShutdownHandler> shutdownHandler,
                ObjectProvider<LeaderElectionService> leaderElection,
                ObjectProvider<com.adhar.kit.kubernetes.config.ConfigMapReloadService> configMapWatch,
                ObjectProvider<com.adhar.kit.kubernetes.config.SecretWatchService> secretWatch) {
            log.info("Registering Kubernetes readiness health indicator");
            return new KubernetesReadinessHealthIndicator(
                    shutdownHandler.getIfAvailable(),
                    leaderElection.getIfAvailable(),
                    configMapWatch.getIfAvailable(),
                    secretWatch.getIfAvailable());
        }

        /**
         * Liveness indicator reporting subsystem liveness (always UP while the
         * context is up).
         */
        @Bean
        @ConditionalOnMissingBean
        public KubernetesLivenessHealthIndicator kubernetesLivenessHealthIndicator(
                ObjectProvider<GracefulShutdownHandler> shutdownHandler,
                ObjectProvider<LeaderElectionService> leaderElection,
                ObjectProvider<com.adhar.kit.kubernetes.config.ConfigMapReloadService> configMapWatch,
                ObjectProvider<com.adhar.kit.kubernetes.config.SecretWatchService> secretWatch) {
            log.info("Registering Kubernetes liveness health indicator");
            return new KubernetesLivenessHealthIndicator(
                    shutdownHandler.getIfAvailable(),
                    leaderElection.getIfAvailable(),
                    configMapWatch.getIfAvailable(),
                    secretWatch.getIfAvailable());
        }
    }
}
