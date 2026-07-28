package com.adhar.kit.health.config;

import com.adhar.kit.health.api.HealthService;
import com.adhar.kit.health.event.HealthEventBroadcaster;
import com.adhar.kit.health.event.SpringHealthEventPublisher;
import com.adhar.kit.health.indicator.AdharHealthIndicator;
import com.adhar.kit.health.indicator.CircuitBreakerHealthIndicator;
import com.adhar.kit.health.integration.SpringBootHealthIntegration;
import com.adhar.kit.health.lifecycle.ReadinessStateManager;
import com.adhar.kit.health.lifecycle.SpringReadinessLifecycle;
import com.adhar.kit.health.registry.HealthRegistry;
import com.adhar.kit.health.registry.RegistryHealthService;
import com.adhar.kit.health.spi.CircuitBreakerStateProvider;
import com.adhar.kit.health.spi.Resilience4jCircuitBreakerStateProvider;
import com.adhar.kit.health.web.HealthEventSseController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot auto-configuration for Adhar Health.
 *
 * <p>Provides a fully wired {@link HealthRegistry} (TTL caching, transition history,
 * built-in indicators), the registry-backed {@link HealthService}, and the readiness
 * gate with graceful-shutdown support. Every bean is {@code @ConditionalOnMissingBean}
 * so applications can override any piece.</p>
 *
 * <p><b>Property toggles (prefix {@code adhar.health}):</b></p>
 * <ul>
 *   <li>{@code enabled} — master switch (default true)</li>
 *   <li>{@code cache.enabled} / {@code cache.ttl} — result caching (default 10s)</li>
 *   <li>{@code memory.enabled} / {@code memory.threshold} — heap indicator</li>
 *   <li>{@code certificate.enabled} + keystore/endpoint settings — cert expiry indicator</li>
 *   <li>{@code readiness-gate.enabled} — readiness gate + shutdown draining</li>
 *   <li>{@code history.*} — transition history capacity and flapping detection</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
@ConditionalOnProperty(prefix = "adhar.health", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties
public class AdharHealthAutoConfiguration {

    /**
     * Binds {@code adhar.health.*} configuration properties.
     *
     * @return health configuration properties
     */
    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties(prefix = "adhar.health")
    public AdharHealthProperties adharHealthProperties() {
        return new AdharHealthProperties();
    }

    /**
     * Creates the health registry, registering all discovered
     * {@link AdharHealthIndicator} beans and applying property-driven configuration.
     *
     * <p>A discovered {@link ReadinessStateManager} bean is routed into the
     * readiness group; all other indicators go to the default group.</p>
     *
     * @param properties health configuration properties
     * @param indicators discovered indicator beans
     * @return configured health registry
     */
    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public HealthRegistry adharHealthRegistry(AdharHealthProperties properties,
                                              ObjectProvider<AdharHealthIndicator> indicators) {
        HealthRegistry registry = new HealthRegistry();
        indicators.forEach(indicator -> {
            if (indicator instanceof ReadinessStateManager) {
                registry.register(indicator, HealthRegistry.READINESS_GROUP);
            } else {
                registry.register(indicator);
            }
        });
        return SpringBootHealthIntegration.configureRegistry(registry, properties);
    }

    /**
     * Creates the registry-backed health service.
     *
     * @param registry health registry
     * @return registry-backed health service
     */
    @Bean
    @ConditionalOnMissingBean(HealthService.class)
    public RegistryHealthService adharRegistryHealthService(HealthRegistry registry) {
        return new RegistryHealthService(registry);
    }

    /**
     * Creates the readiness gate. Being an {@link AdharHealthIndicator} bean it is
     * picked up by {@link #adharHealthRegistry} and registered into the readiness
     * group.
     *
     * @param properties health configuration properties
     * @return readiness gate
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.health.readiness-gate", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ReadinessStateManager adharReadinessStateManager(AdharHealthProperties properties) {
        ReadinessStateManager manager =
                new ReadinessStateManager(properties.getReadinessGate().isInitiallyReady());
        if (properties.getReadinessGate().isShutdownHook()) {
            manager.registerShutdownHook();
        }
        return manager;
    }

    /**
     * Bridges Spring context lifecycle events to the readiness gate so the
     * application drains traffic before shutdown.
     *
     * @param readinessStateManager readiness gate
     * @return lifecycle listener
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ReadinessStateManager.class)
    public SpringReadinessLifecycle adharSpringReadinessLifecycle(ReadinessStateManager readinessStateManager) {
        return new SpringReadinessLifecycle(readinessStateManager);
    }

    /**
     * Creates the health-event broadcaster and attaches it to the registry so health
     * transitions become a stream that SSE clients and custom subscribers can consume.
     *
     * @param registry health registry
     * @return broadcaster registered as a transition listener
     */
    @Bean
    @ConditionalOnMissingBean
    public HealthEventBroadcaster adharHealthEventBroadcaster(HealthRegistry registry) {
        HealthEventBroadcaster broadcaster = new HealthEventBroadcaster();
        registry.addTransitionListener(broadcaster);
        return broadcaster;
    }

    /**
     * Republishes health transitions as Spring {@code ApplicationEvent}s.
     *
     * @param registry  health registry
     * @param publisher application-event publisher
     * @return event-publishing transition listener
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.health.events", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public SpringHealthEventPublisher adharSpringHealthEventPublisher(HealthRegistry registry,
                                                                     ApplicationEventPublisher publisher) {
        SpringHealthEventPublisher eventPublisher = new SpringHealthEventPublisher(publisher);
        registry.addTransitionListener(eventPublisher);
        return eventPublisher;
    }

    /**
     * Creates the circuit-breaker health indicator when at least one
     * {@link CircuitBreakerStateProvider} is present. Being an
     * {@link AdharHealthIndicator} bean it is registered into the registry
     * automatically.
     *
     * @param properties health configuration properties
     * @param providers  discovered circuit-breaker state providers
     * @return circuit-breaker health indicator
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(CircuitBreakerStateProvider.class)
    @ConditionalOnProperty(prefix = "adhar.health.circuit-breaker", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public CircuitBreakerHealthIndicator adharCircuitBreakerHealthIndicator(
            AdharHealthProperties properties, ObjectProvider<CircuitBreakerStateProvider> providers) {
        return new CircuitBreakerHealthIndicator(providers.orderedStream().toList(),
                properties.getCircuitBreaker());
    }

    /**
     * Wires the resilience4j-backed circuit-breaker state provider. Only active when
     * resilience4j is on the classpath and a {@code CircuitBreakerRegistry} bean exists;
     * uses reflection so the health module compiles without resilience4j.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry")
    static class Resilience4jCircuitBreakerConfiguration {

        /**
         * Adapts a resilience4j {@code CircuitBreakerRegistry} to the SPI.
         *
         * @param context application context (used to look up the registry reflectively)
         * @return resilience4j circuit-breaker state provider
         * @throws ClassNotFoundException never, guarded by {@code @ConditionalOnClass}
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnBean(type = "io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry")
        Resilience4jCircuitBreakerStateProvider adharResilience4jCircuitBreakerStateProvider(
                ApplicationContext context) throws ClassNotFoundException {
            Object registry = context.getBean(
                    Class.forName("io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry"));
            return new Resilience4jCircuitBreakerStateProvider(registry);
        }
    }

    /**
     * Exposes the Server-Sent Events health-transition endpoint. Only active when Spring
     * Web MVC is on the classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.web.servlet.mvc.method.annotation.SseEmitter")
    static class HealthEventSseConfiguration {

        /**
         * Creates the SSE controller.
         *
         * @param broadcaster health-event broadcaster
         * @param properties  health configuration properties
         * @return SSE controller
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.health.events", name = "sse-enabled",
                havingValue = "true", matchIfMissing = true)
        HealthEventSseController adharHealthEventSseController(HealthEventBroadcaster broadcaster,
                                                              AdharHealthProperties properties) {
            return new HealthEventSseController(broadcaster, properties.getEvents().getSseTimeout());
        }
    }
}
