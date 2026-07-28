package com.adhar.kit.dapr.config;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.actor.DaprActorProxyFactory;
import com.adhar.kit.dapr.actor.DaprActorRegistrar;
import com.adhar.kit.dapr.aspect.DaprPublishAspect;
import com.adhar.kit.dapr.aspect.DaprStateAspect;
import com.adhar.kit.dapr.client.AdharDaprClient;
import com.adhar.kit.dapr.outbox.OutboxPublisher;
import com.adhar.kit.dapr.outbox.OutboxRelayScheduler;
import com.adhar.kit.dapr.pubsub.DaprEventDispatcher;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionController;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionRegistrar;
import com.adhar.kit.dapr.resilience.DaprInvocationResilience;
import com.adhar.kit.dapr.workflow.DaprWorkflowFacade;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auto-configuration wiring the Dapr module's beans:
 * <ul>
 *   <li>{@link AdharDaprClient} / {@link DaprFacade} - the low-level client and the
 *       framework-agnostic facade.</li>
 *   <li>{@link DaprInvocationResilience} - retry/timeout/circuit-breaker for service
 *       invocation.</li>
 *   <li>{@link DaprStateAspect} / {@link DaprPublishAspect} - enforce the declarative
 *       {@code @DaprState}/{@code @DaprPublish} semantics.</li>
 *   <li>{@link DaprSubscriptionRegistrar} / {@link DaprSubscriptionController} /
 *       {@link DaprEventDispatcher} - {@code @DaprSubscribe}/{@code @DaprTopic} subscription
 *       endpoint registration and CloudEvent dispatch, only when Spring MVC is present.</li>
 * </ul>
 *
 * <p>Distributed lock is backed by the Dapr preview client and works out of the box. Actor
 * runtime registration/proxies ({@link DaprActorRegistrar}/{@link DaprActorProxyFactory}) and
 * the workflow facade ({@link DaprWorkflowFacade}) are wired only when their optional SDKs are
 * on the classpath ({@code @ConditionalOnClass}). The transactional outbox
 * ({@link OutboxPublisher} + {@link OutboxRelayScheduler}) is opt-in via
 * {@code adhar.dapr.outbox.enabled=true}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(DaprProperties.class)
@ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DaprAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AdharDaprClient adharDaprClient() {
        return new AdharDaprClient();
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprFacade daprFacade() {
        return DaprFacade.getInstance();
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprInvocationResilience daprInvocationResilience() {
        return new DaprInvocationResilience();
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprStateAspect daprStateAspect(DaprFacade daprFacade) {
        return new DaprStateAspect(daprFacade);
    }

    @Bean
    @ConditionalOnMissingBean
    public DaprPublishAspect daprPublishAspect(DaprFacade daprFacade) {
        return new DaprPublishAspect(daprFacade);
    }

    /**
     * Subscription endpoint registration/dispatch, only wired when Spring MVC is on the
     * classpath (the module's {@code spring-boot-starter-webmvc} dependency is optional).
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestController.class)
    static class DaprSubscriptionConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public DaprSubscriptionRegistrar daprSubscriptionRegistrar(ApplicationContext applicationContext) {
            return new DaprSubscriptionRegistrar(applicationContext);
        }

        @Bean
        @ConditionalOnMissingBean
        public DaprEventDispatcher daprEventDispatcher() {
            return new DaprEventDispatcher();
        }

        @Bean
        @ConditionalOnMissingBean
        public DaprSubscriptionController daprSubscriptionController(DaprSubscriptionRegistrar registrar,
                                                                       DaprEventDispatcher dispatcher) {
            return new DaprSubscriptionController(registrar, dispatcher);
        }
    }

    /**
     * Actor runtime registration and typed proxies, wired only when the optional
     * {@code dapr-sdk-actors} SDK is on the classpath. The proxy factory is {@code @Lazy} so its
     * underlying {@code ActorClient} is created on first use rather than at context startup.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.dapr.actors.runtime.ActorRuntime")
    @ConditionalOnProperty(prefix = "adhar.dapr.actors", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class DaprActorConfiguration {

        @Bean
        @ConditionalOnMissingBean
        public DaprActorRegistrar daprActorRegistrar() {
            return new DaprActorRegistrar();
        }

        @Bean
        @Lazy
        @ConditionalOnMissingBean
        public DaprActorProxyFactory daprActorProxyFactory() {
            return new DaprActorProxyFactory();
        }
    }

    /**
     * Workflow facade, wired only when the optional {@code dapr-sdk-workflows} SDK is on the
     * classpath. {@code @Lazy} so the underlying {@code DaprWorkflowClient} is created on first
     * use rather than at context startup.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.dapr.workflows.client.DaprWorkflowClient")
    @ConditionalOnProperty(prefix = "adhar.dapr.workflow", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class DaprWorkflowConfiguration {

        @Bean
        @Lazy
        @ConditionalOnMissingBean
        public DaprWorkflowFacade daprWorkflowFacade() {
            return new DaprWorkflowFacade();
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
                outbox.getMaxAttempts());
        }

        @Bean
        @ConditionalOnMissingBean
        public OutboxRelayScheduler outboxRelayScheduler(OutboxPublisher outboxPublisher) {
            return new OutboxRelayScheduler(outboxPublisher);
        }
    }
}
