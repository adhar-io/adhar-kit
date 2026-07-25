package com.adhar.kit.starter.spring;

import com.adhar.kit.starter.AdharFacade;
import com.adhar.kit.starter.AdharFacadeCustomizer;
import com.adhar.kit.starter.AdharModuleAccess;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot wiring for {@link AdharFacade}.
 *
 * <p>Registers the facade as a singleton bean so it can be {@code @Autowired}
 * (constructor injection preferred). Active only when Spring Boot is on the
 * classpath, which keeps this starter usable from non-Spring runtimes.</p>
 *
 * <p>The bean is initialized with the {@link AdharModuleAccess} built from
 * {@code adhar.kit.modules.*} (see {@code AdharKitAutoConfiguration}), so a
 * disabled module is gated from first access. Any {@link AdharFacadeCustomizer}
 * beans in the context are applied right after creation, in declared order,
 * letting apps and tests override individual sub-facades.</p>
 */
@AutoConfiguration
@AutoConfigureAfter(com.adhar.kit.starter.config.AdharKitAutoConfiguration.class)
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
public class SpringAdharFacadeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AdharFacade adharFacade(ObjectProvider<AdharModuleAccess> moduleAccessProvider,
                                    ObjectProvider<AdharFacadeCustomizer> customizers) {
        AdharModuleAccess moduleAccess = moduleAccessProvider.getIfAvailable(() -> AdharModuleAccess.ALL_ENABLED);
        AdharFacade facade = AdharFacade.getInstance(moduleAccess);
        customizers.orderedStream().forEach(customizer -> customizer.customize(facade));
        return facade;
    }
}
