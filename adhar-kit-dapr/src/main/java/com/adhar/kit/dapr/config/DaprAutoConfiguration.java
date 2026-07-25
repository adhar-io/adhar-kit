package com.adhar.kit.dapr.config;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.aspect.DaprPublishAspect;
import com.adhar.kit.dapr.aspect.DaprStateAspect;
import com.adhar.kit.dapr.client.AdharDaprClient;
import com.adhar.kit.dapr.pubsub.DaprEventDispatcher;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionController;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionRegistrar;
import com.adhar.kit.dapr.resilience.DaprInvocationResilience;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
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
 * <p>Distributed lock, actor invocation, and cryptography remain unimplemented (they throw
 * {@link UnsupportedOperationException} in {@link DaprFacade}/{@link AdharDaprClient}) because
 * the pinned Dapr SDK version does not support them from the Java client; no beans are wired
 * for those.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@AutoConfiguration
@EnableAspectJAutoProxy
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
}
