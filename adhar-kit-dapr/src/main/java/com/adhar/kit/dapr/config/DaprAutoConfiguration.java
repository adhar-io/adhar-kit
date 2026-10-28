package com.adhar.kit.dapr.config;

import com.adhar.kit.dapr.DaprComponents;
import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.actor.ActorRuntimeCallbacks;
import com.adhar.kit.dapr.actor.ActorRuntimeConfigurer;
import com.adhar.kit.dapr.actor.DaprActorCallbacks;
import com.adhar.kit.dapr.actor.DaprActorProxyFactory;
import com.adhar.kit.dapr.aspect.DaprBindingAspect;
import com.adhar.kit.dapr.aspect.DaprConfigurationAspect;
import com.adhar.kit.dapr.aspect.DaprInvokeAspect;
import com.adhar.kit.dapr.aspect.DaprLockAspect;
import com.adhar.kit.dapr.aspect.DaprPublishAspect;
import com.adhar.kit.dapr.aspect.DaprStateAspect;
import com.adhar.kit.dapr.backend.DaprBackend;
import com.adhar.kit.dapr.backend.SidecarDaprBackend;
import com.adhar.kit.dapr.client.AdharDaprClient;
import com.adhar.kit.dapr.health.DaprHealthIndicator;
import com.adhar.kit.dapr.http.DaprHttpClient;
import com.adhar.kit.dapr.invoke.DaprServiceClients;
import com.adhar.kit.dapr.memory.InMemoryDaprBackend;
import com.adhar.kit.dapr.observation.DaprObservations;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.adhar.kit.dapr.observation.MicrometerTraceContext;
import com.adhar.kit.dapr.observation.RequestHeaderTraceContext;
import com.adhar.kit.dapr.outbox.OutboxPublisher;
import com.adhar.kit.dapr.outbox.OutboxRelayScheduler;
import com.adhar.kit.dapr.pubsub.DaprEventDispatcher;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionRegistrar;
import com.adhar.kit.dapr.resilience.DaprInvocationResilience;
import com.adhar.kit.dapr.resilience.DaprResilienceRegistry;
import com.adhar.kit.dapr.secrets.DaprSecretInjector;
import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.adhar.kit.dapr.web.DaprAppCallbackHandlerMapping;
import com.adhar.kit.dapr.web.DaprCallbackRegistry;
import com.adhar.kit.dapr.workflow.DaprWorkflowFacade;
import com.adhar.kit.dapr.workflow.DaprWorkflowRuntimeLifecycle;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientImpl;
import io.dapr.client.DaprPreviewClient;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Wires the Dapr module around one SDK client:
 * <ul>
 *   <li>{@link DaprConnectionDetails} and {@link DaprComponents} resolved from
 *       {@code adhar.dapr.*} and the injector's environment;</li>
 *   <li>in sidecar mode one SDK client bean, injectable as {@link DaprClient} and as
 *       {@link DaprPreviewClient}, built with the kit serializer and shared by {@link DaprFacade}
 *       and through it by the cache, messaging and outbox; in memory mode no client at all;</li>
 *   <li>{@link DaprFacade} (also returned by {@link DaprFacade#getInstance()}), per-target
 *       invocation resilience, Micrometer observations and trace propagation;</li>
 *   <li>the startup sidecar wait, a {@code dapr} health indicator, typed service clients
 *       ({@link DaprServiceClients});</li>
 *   <li>the declarative aspects ({@code @DaprState}, {@code @DaprPublish}, {@code @DaprLock},
 *       {@code @DaprBinding}, {@code @DaprConfiguration}, {@code @DaprInvoke}) and
 *       {@code @DaprSecret} field injection;</li>
 *   <li>subscriptions ({@code @DaprSubscribe}/{@code @DaprTopic}/{@code dapr.subscribe}), input
 *       bindings, jobs and the actor runtime served on the app's own HTTP port, ahead of any
 *       {@code @RequestMapping} (and of the Dapr SDK's {@code DaprController});</li>
 *   <li>the actor runtime and the workflow runtime when their SDKs are present, and the outbox
 *       when enabled.</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableConfigurationProperties(DaprProperties.class)
@ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DaprAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public DaprConnectionDetails daprConnectionDetails(DaprProperties properties, Environment environment) {
        return DaprConnectionDetails.resolve(lookup(properties, environment));
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprComponents daprComponents(DaprProperties properties) {
        return properties.toComponents();
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprJsonSerializer daprJsonSerializer() {
        return new DaprJsonSerializer();
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprResilienceRegistry daprResilienceRegistry(DaprProperties properties) {
        return new DaprResilienceRegistry(properties.getServiceInvocation().toResilienceSettings());
    }

    /**
     * The resilience of calls that name no target; kept for applications that injected it.
     * Invocation through the facade uses {@link DaprResilienceRegistry}, one breaker per app id.
     */
    @Bean
    @ConditionalOnMissingBean
    public DaprInvocationResilience daprInvocationResilience(DaprProperties properties) {
        return new DaprInvocationResilience(properties.getServiceInvocation().toResilienceSettings());
    }

    /**
     * The one SDK client, only when there is a sidecar to talk to. The SDK builds a single object
     * that is both the {@link DaprClient} and the {@link DaprPreviewClient}; declaring the bean as
     * that class makes it injectable as either, without a second client and without two beans
     * that would make every {@code DaprClient} injection ambiguous.
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(DaprClient.class)
    @Conditional(OnDaprSidecarCondition.class)
    public DaprClientImpl daprClient(DaprConnectionDetails connection, DaprJsonSerializer serializer) {
        DaprClient client = DaprFacade.Builder.newClient(connection, serializer);
        if (!(client instanceof DaprClientImpl impl)) {
            throw new IllegalStateException("The Dapr SDK built a " + client.getClass().getName()
                    + "; the kit expects DaprClientImpl (both DaprClient and DaprPreviewClient)");
        }
        return impl;
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprFacade daprFacade(DaprConnectionDetails connection, DaprComponents components,
                                 DaprJsonSerializer serializer, DaprProperties properties,
                                 DaprResilienceRegistry resilience, ObjectProvider<DaprTraceContext> traceContexts,
                                 ObjectProvider<DaprClient> client, ObjectProvider<DaprPreviewClient> preview,
                                 ObjectProvider<ObservationRegistry> observationRegistry) {
        DaprTraceContext traceContext = traceContext(traceContexts);
        DaprClient sdkClient = client.getIfAvailable();
        DaprBackend backend;
        if (sdkClient != null) {
            backend = new SidecarDaprBackend(sdkClient, preview.getIfAvailable(),
                    new DaprHttpClient(connection, Duration.ofMillis(properties.getServiceInvocation().getTimeout()),
                            traceContext),
                    serializer, traceContext, false);
        } else {
            backend = memoryBackend(connection, components, serializer, properties);
        }
        DaprFacade facade = new DaprFacade(backend, connection, components, serializer,
                new DaprObservations(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)), resilience);
        DaprFacade.setInstance(facade);
        return facade;
    }

    private static DaprBackend memoryBackend(DaprConnectionDetails connection, DaprComponents components,
                                             DaprJsonSerializer serializer, DaprProperties properties) {
        InMemoryDaprBackend.warnStartup(connection);
        InMemoryDaprBackend memory = new InMemoryDaprBackend(serializer, connection.appId());
        properties.getMemory().getSecrets().forEach((name, values) ->
                memory.putSecret(components.secretStore(), name, values));
        properties.getMemory().getConfiguration().forEach((key, value) ->
                memory.setConfiguration(components.configStore(), key, value));
        return memory;
    }

    /**
     * The shared facade seen through the original client API.
     *
     * @deprecated use {@link DaprFacade}
     */
    @Bean
    @ConditionalOnMissingBean
    @Deprecated(since = "0.2.0")
    public AdharDaprClient adharDaprClient(DaprFacade daprFacade, DaprProperties properties) {
        return new AdharDaprClient(daprFacade, properties.getCrypto().getDefaultKey());
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprSidecarLifecycle daprSidecarLifecycle(DaprFacade daprFacade, DaprProperties properties) {
        return new DaprSidecarLifecycle(daprFacade, properties.getSidecar());
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprEventDispatcher daprEventDispatcher(DaprJsonSerializer serializer) {
        return new DaprEventDispatcher(serializer);
    }

    /**
     * Every subscriber of the application; delivered over HTTP in a servlet app, by streaming
     * subscription otherwise, in-process in memory mode.
     */
    @Bean
    @ConditionalOnMissingBean
    public DaprSubscriptionRegistrar daprSubscriptionRegistrar(ApplicationContext applicationContext,
                                                               DaprFacade daprFacade,
                                                               DaprEventDispatcher dispatcher) {
        return new DaprSubscriptionRegistrar(applicationContext, daprFacade, dispatcher);
    }

    /** {@code @DaprSecret} field injection; static so it does not pull the facade in early. */
    @Bean
    @ConditionalOnMissingBean
    public static DaprSecretInjector daprSecretInjector(ObjectProvider<DaprFacade> daprFacade) {
        return new DaprSecretInjector(daprFacade);
    }

    /**
     * The trace context Dapr calls carry: the current span's (when a tracer is configured), else
     * the inbound request's. Applications can contribute their own {@link DaprTraceContext} beans;
     * they are consulted in {@code @Order}.
     */
    static DaprTraceContext traceContext(ObjectProvider<DaprTraceContext> contexts) {
        List<DaprTraceContext> ordered = contexts.orderedStream().toList();
        if (ordered.isEmpty()) {
            return DaprTraceContext.NONE;
        }
        DaprTraceContext chain = ordered.getLast();
        for (int i = ordered.size() - 2; i >= 0; i--) {
            chain = ordered.get(i).orElse(chain);
        }
        return chain;
    }

    /** Trace context of the current span, when Micrometer Tracing is on the classpath. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class DaprTracingConfiguration {

        @Bean
        @Order(0)
        @ConditionalOnMissingBean
        MicrometerTraceContext daprSpanTraceContext(ObjectProvider<io.micrometer.tracing.Tracer> tracer,
                                                    ObjectProvider<io.micrometer.tracing.propagation.Propagator> propagator) {
            return new MicrometerTraceContext(tracer::getIfAvailable, propagator::getIfAvailable);
        }
    }

    /** Callback endpoints and inbound context, for servlet applications. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnClass(name = "org.springframework.web.servlet.HandlerMapping")
    static class DaprWebConfiguration {

        @Bean
        @Order(10)
        @ConditionalOnMissingBean
        RequestHeaderTraceContext daprInboundTraceContext() {
            return new RequestHeaderTraceContext();
        }

        @Bean
        @ConditionalOnMissingBean
        DaprCallbackRegistry daprCallbackRegistry(ApplicationContext applicationContext, DaprFacade daprFacade,
                                                  DaprJsonSerializer serializer) {
            return new DaprCallbackRegistry(applicationContext, daprFacade, serializer);
        }

        @Bean
        @ConditionalOnMissingBean
        DaprAppCallbackHandlerMapping daprAppCallbackHandlerMapping(DaprSubscriptionRegistrar registrar,
                                                                    DaprCallbackRegistry callbacks,
                                                                    ObjectProvider<DaprActorCallbacks> actors,
                                                                    DaprJsonSerializer serializer,
                                                                    Environment environment) {
            String appToken = environment.getProperty("adhar.dapr.app-api-token",
                    environment.getProperty("APP_API_TOKEN"));
            return new DaprAppCallbackHandlerMapping(registrar, callbacks, actors.getIfAvailable(), serializer,
                    appToken);
        }
    }

    /** Typed clients for other apps through the sidecar. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClient.class)
    static class DaprServiceClientsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DaprServiceClients daprServiceClients(DaprConnectionDetails connection,
                                              ObjectProvider<RestClient.Builder> builder,
                                              DaprResilienceRegistry resilience,
                                              ObjectProvider<DaprTraceContext> traceContexts,
                                              ObjectProvider<ObservationRegistry> observationRegistry) {
            DaprTraceContext traceContext = traceContext(traceContexts);
            RestClient.Builder base = builder.getIfAvailable(() -> RestClient.builder()
                    .observationRegistry(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)));
            boolean servlet = org.springframework.util.ClassUtils.isPresent("jakarta.servlet.http.HttpServletRequest",
                    DaprAutoConfiguration.class.getClassLoader());
            return new DaprServiceClients(connection, base, resilience, traceContext, servlet);
        }
    }

    /** The declarative aspects, when AspectJ is present. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.aspectj.weaver.Advice")
    @EnableAspectJAutoProxy
    static class DaprAspectsConfiguration {

        @Bean
        @ConditionalOnMissingBean
        DaprStateAspect daprStateAspect(DaprFacade daprFacade) {
            return new DaprStateAspect(daprFacade);
        }

        @Bean
        @ConditionalOnMissingBean
        DaprPublishAspect daprPublishAspect(DaprFacade daprFacade) {
            return new DaprPublishAspect(daprFacade);
        }

        @Bean
        @ConditionalOnMissingBean
        DaprLockAspect daprLockAspect(DaprFacade daprFacade) {
            return new DaprLockAspect(daprFacade);
        }

        @Bean
        @ConditionalOnMissingBean
        DaprBindingAspect daprBindingAspect(DaprFacade daprFacade) {
            return new DaprBindingAspect(daprFacade);
        }

        @Bean
        @ConditionalOnMissingBean
        DaprConfigurationAspect daprConfigurationAspect(DaprFacade daprFacade, DaprProperties properties) {
            return new DaprConfigurationAspect(daprFacade, properties.getConfiguration().isSubscribeEnabled());
        }

        @Bean
        @ConditionalOnMissingBean
        DaprInvokeAspect daprInvokeAspect(DaprFacade daprFacade) {
            return new DaprInvokeAspect(daprFacade);
        }
    }

    /** Health of the sidecar, when actuator health is present. */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "org.springframework.boot.health.contributor.HealthIndicator")
    static class DaprHealthConfiguration {

        @Bean(name = "daprHealthIndicator")
        @ConditionalOnMissingBean(name = "daprHealthIndicator")
        DaprHealthIndicator daprHealthIndicator(DaprFacade daprFacade) {
            return new DaprHealthIndicator(daprFacade);
        }
    }

    /**
     * Actor hosting and typed proxies, when {@code dapr-sdk-actors} is on the classpath. Actors are
     * found by scanning the application's packages (or {@code adhar.dapr.actors.scan-packages}).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.dapr.actors.runtime.ActorRuntime")
    @ConditionalOnProperty(prefix = "adhar.dapr.actors", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class DaprActorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        ActorRuntimeConfigurer daprActorRuntimeConfigurer(DaprConnectionDetails connection, DaprProperties properties,
                                                          BeanFactory beanFactory) {
            List<String> packages = properties.getActors().getScanPackages();
            if ((packages == null || packages.isEmpty()) && AutoConfigurationPackages.has(beanFactory)) {
                packages = AutoConfigurationPackages.get(beanFactory);
            }
            ConfigurableListableBeanFactory listable = (ConfigurableListableBeanFactory) beanFactory;
            return new ActorRuntimeConfigurer(connection, properties.getActors(), packages, listable,
                    listable.getBeanClassLoader());
        }

        @Bean
        @ConditionalOnMissingBean
        DaprActorCallbacks daprActorCallbacks(ActorRuntimeConfigurer configurer) {
            return new ActorRuntimeCallbacks(configurer::runtime);
        }

        @Bean
        @Lazy
        @ConditionalOnMissingBean
        DaprActorProxyFactory daprActorProxyFactory(DaprConnectionDetails connection) {
            return new DaprActorProxyFactory(connection);
        }
    }

    /**
     * Workflow client and runtime, when {@code dapr-sdk-workflows} is on the classpath. The client
     * is {@code @Lazy}, so it connects on first use.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.dapr.workflows.client.DaprWorkflowClient")
    @ConditionalOnProperty(prefix = "adhar.dapr.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class DaprWorkflowConfiguration {

        @Bean
        @Lazy
        @ConditionalOnMissingBean
        DaprWorkflowFacade daprWorkflowFacade(DaprConnectionDetails connection,
                                              ObjectProvider<ObservationRegistry> observationRegistry) {
            return new DaprWorkflowFacade(new io.dapr.workflows.client.DaprWorkflowClient(connection.sdkProperties()),
                    new DaprObservations(observationRegistry.getIfAvailable(() -> ObservationRegistry.NOOP)));
        }

        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "adhar.dapr.workflow", name = "register-beans", havingValue = "true",
                matchIfMissing = true)
        DaprWorkflowRuntimeLifecycle daprWorkflowRuntimeLifecycle(ConfigurableListableBeanFactory beanFactory,
                                                                  DaprConnectionDetails connection) {
            return new DaprWorkflowRuntimeLifecycle(beanFactory, connection);
        }
    }

    /**
     * Transactional outbox, opt-in via {@code adhar.dapr.outbox.enabled=true}. Enables
     * scheduling so the relay runs periodically.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(prefix = "adhar.dapr.outbox", name = "enabled", havingValue = "true")
    static class DaprOutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public OutboxPublisher outboxPublisher(DaprFacade daprFacade, DaprProperties properties) {
            DaprProperties.OutboxConfig outbox = properties.getOutbox();
            return new OutboxPublisher(daprFacade, outbox.getStateStore(), outbox.getPubsub(),
                outbox.getMaxAttempts(), outbox.getLockStore());
        }

        @Bean
        @ConditionalOnMissingBean
        public OutboxRelayScheduler outboxRelayScheduler(OutboxPublisher outboxPublisher) {
            return new OutboxRelayScheduler(outboxPublisher);
        }
    }

    /**
     * Connection keys from the bound properties first (so a programmatic or test value wins), then
     * from the environment (which also answers the injector's {@code DAPR_*} variables).
     */
    static Function<String, String> lookup(DaprProperties properties, Environment environment) {
        return key -> {
            String value = switch (key) {
                case "adhar.dapr.mode" -> properties.getMode() == null ? null : properties.getMode().name();
                case "adhar.dapr.http-endpoint" -> properties.getHttpEndpoint();
                case "adhar.dapr.dapr-http-port" -> text(properties.getDaprHttpPort());
                case "adhar.dapr.dapr-port" -> text(properties.getDaprPort());
                case "adhar.dapr.grpc-endpoint" -> properties.getGrpcEndpoint();
                case "adhar.dapr.dapr-grpc-port" -> text(properties.getDaprGrpcPort());
                case "adhar.dapr.api-token" -> properties.getApiToken();
                case "adhar.dapr.app-id" -> properties.getAppId();
                default -> null;
            };
            return value != null ? value : environment.getProperty(key);
        };
    }

    private static String text(Integer value) {
        return value == null ? null : value.toString();
    }
}
