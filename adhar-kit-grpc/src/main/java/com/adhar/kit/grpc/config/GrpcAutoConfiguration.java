package com.adhar.kit.grpc.config;

import com.adhar.kit.grpc.client.AdharGrpcClientFactory;
import com.adhar.kit.grpc.interceptor.ConcurrencyLimitServerInterceptor;
import com.adhar.kit.grpc.interceptor.GrpcObserver;
import com.adhar.kit.grpc.interceptor.MicrometerGrpcObserver;
import com.adhar.kit.grpc.server.AdharGrpcServer;
import com.adhar.kit.grpc.server.GrpcAuthenticator;
import com.adhar.kit.grpc.server.PermitAllGrpcAuthenticator;
import com.adhar.kit.grpc.server.StaticTokenAuthenticator;
import com.adhar.kit.grpc.spring.GrpcClientBeanPostProcessor;
import com.adhar.kit.grpc.spring.GrpcServiceRegistrar;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.SmartLifecycle;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Auto-configuration for Adhar gRPC module.
 *
 * <p>Wires up an {@link AdharGrpcServer} and an {@link AdharGrpcClientFactory}
 * bean, automatically registering any {@code @GrpcService} beans with the
 * server ({@link GrpcServiceRegistrar}) and injecting {@code ManagedChannel}s
 * into {@code @GrpcClient} fields ({@link GrpcClientBeanPostProcessor}).</p>
 *
 * <p><b>Features:</b></p>
 * <ul>
 *   <li>Automatic gRPC server lifecycle management</li>
 *   <li>Service discovery via {@code @GrpcService} annotation</li>
 *   <li>Client channel injection via {@code @GrpcClient} annotation</li>
 *   <li>Optional authentication ({@code adhar.grpc.auth.enabled})</li>
 *   <li>Optional Micrometer metrics, when {@code MeterRegistry} is on the classpath and a bean exists</li>
 *   <li>Configurable server port and options</li>
 * </ul>
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   grpc:
 *     enabled: true
 *     server:
 *       port: 9090
 *       max-inbound-message-size: 4194304
 *       max-inbound-metadata-size: 8192
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(GrpcProperties.class)
@ConditionalOnClass(name = "io.grpc.Server")
@ConditionalOnProperty(prefix = "adhar.grpc", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GrpcAutoConfiguration {

    private final GrpcProperties grpcProperties;

    public GrpcAutoConfiguration(GrpcProperties grpcProperties) {
        this.grpcProperties = grpcProperties;
    }

    @PostConstruct
    public void logGrpcConfiguration() {
        log.info("Adhar gRPC module initialized - server port: {}", grpcProperties.getServer().getPort());
    }

    /**
     * Default authenticator: permit-all unless {@code adhar.grpc.auth.enabled}
     * is set and a shared secret is configured, in which case a
     * {@link StaticTokenAuthenticator} is used. Applications can override this
     * bean to plug in their own {@link GrpcAuthenticator}.
     *
     * @return the authenticator bean
     */
    @Bean
    @ConditionalOnMissingBean
    public GrpcAuthenticator grpcAuthenticator() {
        GrpcProperties.AuthConfig auth = grpcProperties.getAuth();
        if (auth.isEnabled() && auth.getSharedSecret() != null && !auth.getSharedSecret().isBlank()) {
            return new StaticTokenAuthenticator(auth.getSharedSecret());
        }
        return new PermitAllGrpcAuthenticator();
    }

    /**
     * Creates the shared {@link AdharGrpcClientFactory} used to resolve
     * named channels for {@code @GrpcClient} injection.
     *
     * @return the client factory bean
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharGrpcClientFactory adharGrpcClientFactory() {
        return new AdharGrpcClientFactory(grpcProperties);
    }

    /**
     * Creates the {@link AdharGrpcServer} bean. Services are added later by
     * {@link GrpcServiceRegistrar}; this bean only configures cross-cutting
     * concerns (auth) that must be known at construction/start time.
     *
     * @param grpcAuthenticator authenticator applied when auth is enabled
     * @return the server bean
     */
    @Bean
    @ConditionalOnMissingBean
    public AdharGrpcServer adharGrpcServer(GrpcAuthenticator grpcAuthenticator) {
        AdharGrpcServer server = new AdharGrpcServer(grpcProperties);
        if (grpcProperties.getAuth().isEnabled()) {
            server.withAuthenticator(grpcAuthenticator);
        }
        return server;
    }

    /**
     * Registers {@code @GrpcService} beans with the {@link AdharGrpcServer}.
     *
     * @param applicationContext context to scan for {@code @GrpcService} beans
     * @param adharGrpcServer    server bean to register services with
     * @return the registrar bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.grpc", name = "enable-service-registrar",
            havingValue = "true", matchIfMissing = true)
    public GrpcServiceRegistrar grpcServiceRegistrar(ApplicationContext applicationContext,
                                                      AdharGrpcServer adharGrpcServer) {
        return new GrpcServiceRegistrar(applicationContext, adharGrpcServer);
    }

    /**
     * Injects {@code ManagedChannel}s into {@code @GrpcClient} fields.
     *
     * @param adharGrpcClientFactory factory used to resolve named channels
     * @return the bean post-processor bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.grpc", name = "enable-client-injection",
            havingValue = "true", matchIfMissing = true)
    public static GrpcClientBeanPostProcessor grpcClientBeanPostProcessor(AdharGrpcClientFactory adharGrpcClientFactory) {
        return new GrpcClientBeanPostProcessor(adharGrpcClientFactory);
    }

    /**
     * Starts/stops the {@link AdharGrpcServer} as part of the Spring
     * application lifecycle, running in the last possible phase so that all
     * {@code @GrpcService} beans have already been registered by
     * {@link GrpcServiceRegistrar} (a {@code SmartInitializingSingleton},
     * which always runs before any {@code SmartLifecycle}).
     *
     * @param adharGrpcServer server to manage
     * @return the lifecycle bean
     */
    @Bean
    @ConditionalOnMissingBean
    public SmartLifecycle adharGrpcServerLifecycle(AdharGrpcServer adharGrpcServer) {
        return new AdharGrpcServerLifecycle(adharGrpcServer);
    }

    /**
     * Creates the {@link ConcurrencyLimitServerInterceptor} that bounds
     * concurrent in-flight calls, when {@code adhar.grpc.concurrency.enabled}
     * is {@code true}. The interceptor is wired into the server by
     * {@link #grpcInterceptorBinder}. Applications can override this bean to
     * supply their own limiter.
     *
     * @return the concurrency-limit interceptor bean
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "adhar.grpc.concurrency", name = "enabled", havingValue = "true")
    public ConcurrencyLimitServerInterceptor grpcConcurrencyLimitInterceptor() {
        GrpcProperties.ConcurrencyConfig concurrency = grpcProperties.getConcurrency();
        return new ConcurrencyLimitServerInterceptor(
                concurrency.getGlobalLimit(), concurrency.getServiceLimits());
    }

    /**
     * Wires the optional {@link GrpcObserver} (for tracing spans) and the
     * optional {@link ConcurrencyLimitServerInterceptor} into the
     * {@link AdharGrpcServer} and {@link AdharGrpcClientFactory} once all beans
     * are constructed but before the server is started (which happens in the
     * last {@code SmartLifecycle} phase). Both are looked up lazily via
     * {@link ObjectProvider} so their absence is not an error - the tracing
     * interceptors then degrade to W3C {@code traceparent} + MDC correlation
     * only, and no concurrency limiting is applied.
     *
     * @param adharGrpcServer        server to wire into
     * @param adharGrpcClientFactory client factory to wire into
     * @param observerProvider       optional observer provider
     * @param concurrencyProvider    optional concurrency-limit interceptor provider
     * @return the binder bean
     */
    @Bean
    public InitializingBean grpcInterceptorBinder(AdharGrpcServer adharGrpcServer,
                                                  AdharGrpcClientFactory adharGrpcClientFactory,
                                                  ObjectProvider<GrpcObserver> observerProvider,
                                                  ObjectProvider<ConcurrencyLimitServerInterceptor> concurrencyProvider) {
        return () -> {
            GrpcObserver observer = observerProvider.getIfAvailable();
            if (observer != null) {
                adharGrpcServer.withGrpcObserver(observer);
                adharGrpcClientFactory.withGrpcObserver(observer);
                log.debug("gRPC tracing observer wired: {}", observer.getClass().getSimpleName());
            }
            ConcurrencyLimitServerInterceptor limiter = concurrencyProvider.getIfAvailable();
            if (limiter != null) {
                adharGrpcServer.withConcurrencyLimitInterceptor(limiter);
            }
        };
    }

    /**
     * Wires a {@link MeterRegistry} into the server and client factory when
     * micrometer-core is on the classpath, a {@code MeterRegistry} bean is
     * available, and metrics are enabled. Isolated in a nested
     * {@code @Configuration} class so the outer auto-configuration loads
     * cleanly even when micrometer-core is absent from the consuming
     * application's classpath.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class GrpcMetricsConfiguration {

        @Bean
        @ConditionalOnBean(MeterRegistry.class)
        @ConditionalOnProperty(prefix = "adhar.grpc.observability", name = "enable-metrics",
                havingValue = "true", matchIfMissing = true)
        public InitializingBean grpcMetricsBinder(MeterRegistry meterRegistry,
                                                   AdharGrpcServer adharGrpcServer,
                                                   AdharGrpcClientFactory adharGrpcClientFactory) {
            return () -> {
                adharGrpcServer.withMeterRegistry(meterRegistry);
                adharGrpcClientFactory.withMeterRegistry(meterRegistry);
            };
        }
    }

    /**
     * Provides a Micrometer-backed {@link GrpcObserver} so gRPC tracing
     * interceptors record real spans, but only when micrometer-observation is
     * on the classpath ({@code @ConditionalOnClass(ObservationRegistry.class)})
     * and an {@code ObservationRegistry} bean is available. Isolated in a nested
     * {@code @Configuration} class - which references
     * {@code io.micrometer.observation} types - so the outer auto-configuration
     * loads cleanly even when micrometer-observation is absent, in which case
     * the tracing interceptors fall back to W3C {@code traceparent} + MDC
     * correlation only.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ObservationRegistry.class)
    @ConditionalOnProperty(prefix = "adhar.grpc.observability", name = "enable-tracing",
            havingValue = "true", matchIfMissing = true)
    static class GrpcTracingConfiguration {

        @Bean
        @ConditionalOnMissingBean(GrpcObserver.class)
        @ConditionalOnBean(ObservationRegistry.class)
        public GrpcObserver micrometerGrpcObserver(ObservationRegistry observationRegistry) {
            return new MicrometerGrpcObserver(observationRegistry);
        }
    }

    /**
     * {@link SmartLifecycle} adapter that starts/stops an {@link AdharGrpcServer}.
     */
    private static class AdharGrpcServerLifecycle implements SmartLifecycle {

        private final AdharGrpcServer adharGrpcServer;
        private volatile boolean running = false;

        private AdharGrpcServerLifecycle(AdharGrpcServer adharGrpcServer) {
            this.adharGrpcServer = adharGrpcServer;
        }

        @Override
        public void start() {
            try {
                adharGrpcServer.start();
                running = true;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start gRPC server", e);
            }
        }

        @Override
        public void stop() {
            adharGrpcServer.shutdown();
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return Integer.MAX_VALUE;
        }
    }
}
