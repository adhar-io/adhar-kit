package com.dbs.adhar.health.config;

import com.dbs.adhar.health.AdharHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HealthAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public AdharHealthIndicator adharHealthIndicator() {
        return new AdharHealthIndicator();
    }
}

