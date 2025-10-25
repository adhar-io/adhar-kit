package com.adhar.kit.analytics.config;

import com.adhar.kit.analytics.event.AnalyticsEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Auto-configuration for Adhar Analytics module.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@ComponentScan(basePackages = "com.adhar.kit.analytics")
@EnableConfigurationProperties(AnalyticsProperties.class)
@ConditionalOnProperty(prefix = "adhar.analytics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AnalyticsAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "adhar.analytics.event-tracking", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ProducerFactory<String, AnalyticsEvent> analyticsEventProducerFactory() {
        log.info("Initializing Analytics Event Producer Factory");

        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "1");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);

        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    @ConditionalOnProperty(prefix = "adhar.analytics.event-tracking", name = "enabled", havingValue = "true", matchIfMissing = true)
    public KafkaTemplate<String, AnalyticsEvent> analyticsKafkaTemplate(
            ProducerFactory<String, AnalyticsEvent> producerFactory) {
        log.info("Initializing Analytics Kafka Template");
        return new KafkaTemplate<>(producerFactory);
    }
}

