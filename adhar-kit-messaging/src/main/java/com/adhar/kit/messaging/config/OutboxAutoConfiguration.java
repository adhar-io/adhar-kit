package com.adhar.kit.messaging.config;

import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.outbox.InMemoryOutboxStore;
import com.adhar.kit.messaging.outbox.JdbcOutboxStore;
import com.adhar.kit.messaging.outbox.OutboxPayloadCodec;
import com.adhar.kit.messaging.outbox.OutboxRelay;
import com.adhar.kit.messaging.outbox.OutboxStore;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Auto-configuration for the transactional outbox. Registered after
 * {@link MessagingAutoConfiguration} so that its {@code @ConditionalOnBean(MessagePublisher)}
 * relay reliably sees the broker-backed {@link MessagePublisher} bean created there.
 * <p>
 * Active only when {@code adhar.messaging.outbox.enabled=true}. It registers:
 * <ul>
 *   <li>an {@link OutboxStore} - JDBC-backed ({@link JdbcOutboxStore}) when {@code spring-jdbc}
 *       is on the classpath and a {@link DataSource} bean exists, in-memory
 *       ({@link InMemoryOutboxStore}) otherwise;</li>
 *   <li>an {@link OutboxPayloadCodec};</li>
 *   <li>a scheduled {@link OutboxRelay}, but only when a {@link MessagePublisher} is present
 *       (there is nothing to relay to otherwise).</li>
 * </ul>
 * Every bean is guarded with {@code @ConditionalOnMissingBean} so applications can override
 * any of them.
 */
@AutoConfiguration(after = MessagingAutoConfiguration.class)
@EnableConfigurationProperties(AdharMessagingProperties.class)
@ConditionalOnProperty(prefix = "adhar.messaging.outbox", name = "enabled", havingValue = "true")
public class OutboxAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OutboxAutoConfiguration.class);

    /**
     * JDBC-backed {@link OutboxStore}, registered only when {@code spring-jdbc} is on the
     * classpath and a {@link DataSource} bean is available. Declared as a nested class so it
     * is processed before {@link #inMemoryOutboxStore}, letting it win the
     * {@code @ConditionalOnMissingBean} race.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass({DataSource.class, JdbcTemplate.class})
    public static class JdbcOutboxConfiguration {

        @Bean
        @ConditionalOnMissingBean(OutboxStore.class)
        @ConditionalOnBean(DataSource.class)
        public OutboxStore jdbcOutboxStore(DataSource dataSource, AdharMessagingProperties properties) {
            log.debug("Registering JDBC-backed OutboxStore");
            return new JdbcOutboxStore(new JdbcTemplate(dataSource), properties.getOutbox());
        }
    }

    @Bean
    @ConditionalOnMissingBean(OutboxStore.class)
    public OutboxStore inMemoryOutboxStore() {
        log.debug("Registering in-memory OutboxStore");
        return new InMemoryOutboxStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutboxPayloadCodec outboxPayloadCodec(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new OutboxPayloadCodec(objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    @Bean(initMethod = "start", destroyMethod = "stop")
    @ConditionalOnMissingBean
    @ConditionalOnBean(MessagePublisher.class)
    public OutboxRelay outboxRelay(OutboxStore outboxStore, MessagePublisher messagePublisher,
                                   OutboxPayloadCodec outboxPayloadCodec, AdharMessagingProperties properties,
                                   ObjectProvider<MessagingMetrics> metricsProvider) {
        log.debug("Registering scheduled OutboxRelay");
        return new OutboxRelay(outboxStore, messagePublisher, outboxPayloadCodec,
                properties.getOutbox(), metricsProvider.getIfAvailable());
    }
}
