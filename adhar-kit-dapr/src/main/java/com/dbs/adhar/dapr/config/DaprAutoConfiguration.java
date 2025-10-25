package com.dbs.adhar.dapr.config;

import com.dbs.adhar.dapr.DaprLifecycle;
import io.dapr.client.DaprClient;
import io.dapr.client.DaprClientBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Dapr integration.
 */
@Configuration
@EnableConfigurationProperties(DaprProperties.class)
@ConditionalOnProperty(prefix = "adhar.dapr", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DaprAutoConfiguration {

    @Bean
    public DaprLifecycle daprLifecycle() {
        return new DaprLifecycle();
    }

    @Bean
    public DaprClient daprClient() {
        return new DaprClientBuilder().build();
    }
}

