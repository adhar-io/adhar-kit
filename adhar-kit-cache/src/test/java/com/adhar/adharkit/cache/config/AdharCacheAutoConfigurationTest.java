package com.adhar.adharkit.cache.config;

import com.adhar.adharkit.cache.service.AdharCacheKafkaClients;
import com.adhar.adharkit.cache.service.KafkaCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Wiring of the Kafka-synchronized cache. No broker is needed: topic creation is
 * switched off, and nothing here sends.
 */
class AdharCacheAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisConfig.class, AdharCacheAutoConfiguration.class))
            .withPropertyValues(
                    "adhar.cache.redis.enabled=false",
                    "adhar.cache.kafka.auto-create-topics=false",
                    "spring.kafka.bootstrap-servers=localhost:1");

    @Test
    void kafkaOnTheClasspathDoesNotEnableSynchronization() {
        runner.run(ctx -> assertThat(ctx)
                .doesNotHaveBean(AdharCacheKafkaClients.class)
                .doesNotHaveBean(CacheManager.class));
    }

    @Test
    void synchronizationLeavesTheApplicationsKafkaBeansAlone() {
        // Any of these would make Spring Boot's Kafka auto-configuration back off,
        // taking the application's own KafkaTemplate and listener setup with it.
        runner.withPropertyValues("adhar.cache.kafka.enabled=true").run(ctx -> assertThat(ctx)
                .hasSingleBean(KafkaCacheManager.class)
                .doesNotHaveBean(ProducerFactory.class)
                .doesNotHaveBean(ConsumerFactory.class)
                .doesNotHaveBean(KafkaTemplate.class)
                .doesNotHaveBean(KafkaAdmin.class)
                .doesNotHaveBean(com.fasterxml.jackson.databind.ObjectMapper.class));
    }

    @Test
    void bootstrapServersDefaultToSpringBoots() {
        runner.withPropertyValues("adhar.cache.kafka.enabled=true").run(ctx ->
                assertThat(ctx.getBean(AdharCacheKafkaClients.class).getBootstrapServers()).isEqualTo("localhost:1"));
        runner.withPropertyValues("adhar.cache.kafka.enabled=true", "adhar.cache.kafka.bootstrap-servers=127.0.0.1:2")
                .run(ctx -> assertThat(ctx.getBean(AdharCacheKafkaClients.class).getBootstrapServers())
                        .isEqualTo("127.0.0.1:2"));
    }

    @Test
    void everyInstanceConsumesInItsOwnGroup() {
        // A shared group delivers each invalidation to one instance only.
        AtomicReference<String> first = new AtomicReference<>();
        ApplicationContextRunner enabled = runner.withPropertyValues("adhar.cache.kafka.enabled=true");
        enabled.run(ctx -> first.set(ctx.getBean(AdharCacheKafkaClients.class).getGroupId()));
        enabled.run(ctx -> assertThat(ctx.getBean(AdharCacheKafkaClients.class).getGroupId())
                .startsWith("adhar-cache-group-")
                .isNotEqualTo(first.get()));
    }

    @Test
    void anApplicationCacheManagerWins() {
        runner.withPropertyValues("adhar.cache.kafka.enabled=true")
                .withUserConfiguration(ApplicationCacheManager.class)
                .run(ctx -> assertThat(ctx)
                        .doesNotHaveBean(KafkaCacheManager.class)
                        .doesNotHaveBean(AdharCacheKafkaClients.class)
                        .getBean(CacheManager.class).isInstanceOf(ConcurrentMapCacheManager.class));
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationCacheManager {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager();
        }
    }
}
