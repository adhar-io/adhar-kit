package com.adhar.kit.messaging.config;

import com.adhar.kit.messaging.cloudevents.CloudEventAdapter;
import com.adhar.kit.messaging.core.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.cloudevents.CloudEvent;

/**
 * Auto-configuration for CloudEvent support.
 * <p>
 * This class provides automatic configuration for CloudEvent support when included in a Spring Boot application.
 * It sets up the necessary beans for converting between {@link Message} and
 * {@link io.cloudevents.CloudEvent}.
 * <p>
 * The auto-configuration can be enabled or disabled using the {@code adhar.messaging.cloudevents.enabled} property.
 * By default, it is enabled.
 */
@Configuration
@ConditionalOnClass(CloudEvent.class)
@ConditionalOnProperty(prefix = "adhar.messaging.cloudevents", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CloudEventAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CloudEventAutoConfiguration.class);

    /**
     * Creates a CloudEventAdapter bean.
     * <p>
     * This bean is used to convert between {@link Message} and
     * {@link io.cloudevents.CloudEvent}.
     *
     * @return the CloudEventAdapter bean
     */
    @Bean
    @ConditionalOnMissingBean
    public CloudEventAdapter cloudEventAdapter() {
        log.debug("Creating CloudEventAdapter bean");
        return new CloudEventAdapter();
    }
}