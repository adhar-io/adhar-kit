package com.adhar.kit.starter.spring;

import com.adhar.kit.starter.AdharFacade;
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
 */
@AutoConfiguration
@ConditionalOnClass(name = "org.springframework.boot.SpringApplication")
public class SpringAdharFacadeConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AdharFacade adharFacade() {
        return AdharFacade.getInstance();
    }
}
