package com.adhar.kit.persistence.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Adhar Persistence module.
 *
 * <p><b>Configuration Example:</b></p>
 * <pre>{@code
 * adhar:
 *   persistence:
 *     enabled: true
 *     enable-auditing: true
 *     enable-multi-tenancy: false
 *     default-batch-size: 50
 *     metrics:
 *       enabled: true
 *       slow-query-threshold-ms: 500
 *     outbox:
 *       enabled: false
 *       poll-interval-ms: 5000
 *       batch-size: 100
 *     connection-pool:
 *       maximum-pool-size: 20
 *       minimum-idle: 5
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.persistence")
public class PersistenceProperties {
    private boolean enabled = true;
    private boolean enableAuditing = true;
    private boolean enableMultiTenancy = false;
    private MultiTenancyStrategy multiTenancyStrategy = MultiTenancyStrategy.SCHEMA;
    private boolean enableSoftDelete = true;
    private int defaultBatchSize = 50;
    private Migration migration = new Migration();
    private ConnectionPool connectionPool = new ConnectionPool();
    private Metrics metrics = new Metrics();
    private Outbox outbox = new Outbox();
    private Multitenancy multitenancy = new Multitenancy();
    private Envers envers = new Envers();
    private Diagnostics diagnostics = new Diagnostics();
    private Dapr dapr = new Dapr();

    public enum MultiTenancyStrategy {
        SCHEMA, DATABASE, DISCRIMINATOR
    }

    @Data
    public static class Migration {
        private boolean enabled = true;
        private boolean cleanOnValidationError = false;
        private boolean baselineOnMigrate = true;
        private String locations = "classpath:db/migration";
    }

    @Data
    public static class ConnectionPool {
        private String poolName = "AdharHikariPool";
        private int maximumPoolSize = 10;
        private int minimumIdle = 5;
        private long connectionTimeout = 30000;
        private long idleTimeout = 600000;
        private long maxLifetime = 1800000;
    }

    @Data
    public static class Metrics {
        private boolean enabled = true;
        private long slowQueryThresholdMs = 500;
    }

    @Data
    public static class Outbox {
        private boolean enabled = false;
        private long pollIntervalMs = 5000;
        private int batchSize = 100;

        /** Maximum publish attempts before an event is moved to the {@code DEAD} state. */
        private int maxAttempts = 5;

        /** Backoff before the first retry, in milliseconds. */
        private long initialBackoffMs = 1000;

        /** Multiplier applied to the backoff after each failed attempt (exponential backoff). */
        private double backoffMultiplier = 2.0;

        /** Upper bound applied to the computed backoff, in milliseconds. */
        private long maxBackoffMs = 60_000;

        /**
         * Whether {@link com.adhar.kit.persistence.outbox.DomainEventOutboxBridge} is registered
         * to persist {@link com.adhar.kit.persistence.outbox.OutboxedEvent} application events as
         * outbox rows automatically.
         */
        private boolean bridgeEnabled = false;

        /**
         * Selects which {@link com.adhar.kit.persistence.outbox.OutboxRelay} implementation the
         * auto-configuration wires up when the consumer has not supplied their own bean.
         *
         * <ul>
         *   <li>{@code application-event} (default) -- republish through Spring's
         *       {@code ApplicationEventPublisher}
         *       ({@link com.adhar.kit.persistence.outbox.ApplicationEventOutboxRelay}).</li>
         *   <li>{@code kafka} -- publish onto Kafka
         *       ({@link com.adhar.kit.persistence.outbox.KafkaOutboxRelay}); requires
         *       {@code spring-kafka} and a {@code KafkaTemplate} bean on the classpath.</li>
         * </ul>
         */
        private String relay = "application-event";

        /** Kafka-specific settings for the {@code kafka} relay. */
        private Kafka kafka = new Kafka();

        /**
         * Settings for {@link com.adhar.kit.persistence.outbox.KafkaOutboxRelay}.
         */
        @Data
        public static class Kafka {
            /** Topic that outbox events are published to. */
            private String topic = "adhar.outbox.events";
        }
    }

    /**
     * Hibernate Envers revision-history settings. Only takes effect when {@code hibernate-envers}
     * is on the classpath; the auto-configuration then registers a
     * {@link com.adhar.kit.persistence.envers.RevisionHistoryReader} helper.
     */
    @Data
    public static class Envers {
        private boolean enabled = true;
    }

    /**
     * Development-time persistence diagnostics.
     */
    @Data
    public static class Diagnostics {
        private NPlusOne nPlusOne = new NPlusOne();

        /**
         * N+1 query detector settings. Disabled by default; when enabled, a Hibernate
         * {@code StatementInspector} counts identical statements executed on the same thread and
         * logs a warning once a single statement is repeated more than {@code threshold} times.
         */
        @Data
        public static class NPlusOne {
            private boolean enabled = false;
            /** Repetitions of an identical statement (on one thread) that trigger a warning. */
            private int threshold = 10;
        }
    }

    /**
     * Nested multi-tenancy configuration consumed by the real Hibernate wiring in
     * {@code PersistenceAutoConfiguration} (as opposed to the legacy flat
     * {@code enable-multi-tenancy} / {@code multi-tenancy-strategy} properties, kept for backward
     * compatibility).
     */
    @Data
    public static class Multitenancy {
        private boolean enabled = false;
        private MultiTenancyStrategy strategy = MultiTenancyStrategy.SCHEMA;
    }

    /**
     * Dapr state-store repository settings ({@code adhar.persistence.dapr.*}).
     * The repository only activates when the Dapr module is on the classpath
     * and {@code adhar.dapr.enabled=true}.
     */
    @Data
    public static class Dapr {
        /**
         * Enable the Dapr state-store backed key-value repository.
         */
        private boolean enabled = true;

        /**
         * Dapr state store component name used by {@code DaprStateRepository}.
         */
        private String stateStore = "statestore";
    }
}
