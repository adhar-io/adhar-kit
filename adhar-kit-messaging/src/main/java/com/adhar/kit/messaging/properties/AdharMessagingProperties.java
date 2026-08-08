package com.adhar.kit.messaging.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Adhar messaging.
 * <p>
 * This class provides a comprehensive set of configuration options for the Adhar messaging system.
 * It supports both Kafka and RabbitMQ messaging systems with sensible defaults provided
 * for all options.
 * <p>
 * The configuration is organized hierarchically, allowing for fine-grained control over
 * different aspects of the messaging system.
 */
@ConfigurationProperties(prefix = "adhar.messaging")
public class AdharMessagingProperties {

    /**
     * Whether to enable the Adhar messaging features.
     */
    private boolean enabled = true;

    /**
     * Configuration for Kafka messaging.
     */
    private final KafkaProperties kafka = new KafkaProperties();

    /**
     * Configuration for RabbitMQ messaging.
     */
    private final RabbitMQProperties rabbitmq = new RabbitMQProperties();

    /**
     * Configuration for Dapr pub/sub messaging.
     */
    private final DaprProperties dapr = new DaprProperties();

    /**
     * Configuration for common messaging properties.
     */
    private final CommonProperties common = new CommonProperties();

    /**
     * Configuration for the transactional outbox
     * ({@code adhar.messaging.outbox.*}). Opt-in: disabled by default.
     */
    private final OutboxProperties outbox = new OutboxProperties();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public KafkaProperties getKafka() {
        return kafka;
    }

    public RabbitMQProperties getRabbitmq() {
        return rabbitmq;
    }

    public DaprProperties getDapr() {
        return dapr;
    }

    public CommonProperties getCommon() {
        return common;
    }

    public OutboxProperties getOutbox() {
        return outbox;
    }

    /**
     * Properties for Kafka messaging.
     */
    public static class KafkaProperties {
        /**
         * Whether to enable Kafka messaging.
         */
        private boolean enabled = true;

        /**
         * Bootstrap servers for Kafka.
         */
        private String bootstrapServers = "localhost:9092";

        /**
         * Default topic to use when one is not specified.
         */
        private String defaultTopic = "default-topic";

        /**
         * Properties for Kafka producer.
         */
        private final ProducerProperties producer = new ProducerProperties();

        /**
         * Properties for Kafka consumer.
         */
        private final ConsumerProperties consumer = new ConsumerProperties();

        /**
         * Additional properties for Kafka client.
         */
        private Map<String, String> properties = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getDefaultTopic() {
            return defaultTopic;
        }

        public void setDefaultTopic(String defaultTopic) {
            this.defaultTopic = defaultTopic;
        }

        public ProducerProperties getProducer() {
            return producer;
        }

        public ConsumerProperties getConsumer() {
            return consumer;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        /**
         * Properties for Kafka producer.
         */
        public static class ProducerProperties {
            /**
             * Transaction ID prefix.
             */
            private String transactionIdPrefix;

            /**
             * Default topic to which messages will be sent.
             */
            private String defaultTopic;

            /**
             * Number of acknowledgments the producer requires the leader to have received before considering a request complete.
             */
            private String acks = "all";

            /**
             * Number of retries when sending messages.
             */
            private int retries = 3;

            /**
             * Batch size in bytes.
             */
            private int batchSize = 16384;

            /**
             * Additional properties for Kafka producer.
             */
            private Map<String, String> properties = new HashMap<>();

            public String getTransactionIdPrefix() {
                return transactionIdPrefix;
            }

            public void setTransactionIdPrefix(String transactionIdPrefix) {
                this.transactionIdPrefix = transactionIdPrefix;
            }

            public String getDefaultTopic() {
                return defaultTopic;
            }

            public void setDefaultTopic(String defaultTopic) {
                this.defaultTopic = defaultTopic;
            }

            public String getAcks() {
                return acks;
            }

            public void setAcks(String acks) {
                this.acks = acks;
            }

            public int getRetries() {
                return retries;
            }

            public void setRetries(int retries) {
                this.retries = retries;
            }

            public int getBatchSize() {
                return batchSize;
            }

            public void setBatchSize(int batchSize) {
                this.batchSize = batchSize;
            }

            public Map<String, String> getProperties() {
                return properties;
            }

            public void setProperties(Map<String, String> properties) {
                this.properties = properties;
            }
        }

        /**
         * Properties for Kafka consumer.
         */
        public static class ConsumerProperties {
            /**
             * Group ID for the consumer.
             */
            private String groupId = "adhar-consumer-group";

            /**
             * Auto offset reset strategy.
             */
            private String autoOffsetReset = "latest";

            /**
             * Whether to enable auto-commit.
             */
            private boolean enableAutoCommit = true;

            /**
             * Maximum number of records returned in a single call to poll().
             */
            private int maxPollRecords = 500;

            /**
             * Additional properties for Kafka consumer.
             */
            private Map<String, String> properties = new HashMap<>();

            public String getGroupId() {
                return groupId;
            }

            public void setGroupId(String groupId) {
                this.groupId = groupId;
            }

            public String getAutoOffsetReset() {
                return autoOffsetReset;
            }

            public void setAutoOffsetReset(String autoOffsetReset) {
                this.autoOffsetReset = autoOffsetReset;
            }

            public boolean isEnableAutoCommit() {
                return enableAutoCommit;
            }

            public void setEnableAutoCommit(boolean enableAutoCommit) {
                this.enableAutoCommit = enableAutoCommit;
            }

            public int getMaxPollRecords() {
                return maxPollRecords;
            }

            public void setMaxPollRecords(int maxPollRecords) {
                this.maxPollRecords = maxPollRecords;
            }

            public Map<String, String> getProperties() {
                return properties;
            }

            public void setProperties(Map<String, String> properties) {
                this.properties = properties;
            }
        }
    }

    /**
     * Properties for RabbitMQ messaging.
     */
    public static class RabbitMQProperties {
        /**
         * Whether to enable RabbitMQ messaging.
         */
        private boolean enabled = true;

        /**
         * RabbitMQ host.
         */
        private String host = "localhost";

        /**
         * RabbitMQ port.
         */
        private int port = 5672;

        /**
         * RabbitMQ username.
         */
        private String username = "guest";

        /**
         * RabbitMQ password.
         */
        private String password = "guest";

        /**
         * RabbitMQ virtual host.
         */
        private String virtualHost = "/";

        /**
         * Default exchange to use when one is not specified.
         */
        private String defaultExchange = "adhar.default";

        /**
         * Default routing key to use when one is not specified.
         */
        private String defaultRoutingKey = "adhar.default";

        /**
         * Default queue to use when one is not specified.
         */
        private String defaultQueue = "adhar.default";

        /**
         * Properties for RabbitMQ publisher.
         */
        private final PublisherProperties publisher = new PublisherProperties();

        /**
         * Properties for RabbitMQ listener.
         */
        private final ListenerProperties listener = new ListenerProperties();

        /**
         * Additional properties for RabbitMQ client.
         */
        private Map<String, String> properties = new HashMap<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getVirtualHost() {
            return virtualHost;
        }

        public void setVirtualHost(String virtualHost) {
            this.virtualHost = virtualHost;
        }

        public String getDefaultExchange() {
            return defaultExchange;
        }

        public void setDefaultExchange(String defaultExchange) {
            this.defaultExchange = defaultExchange;
        }

        public String getDefaultRoutingKey() {
            return defaultRoutingKey;
        }

        public void setDefaultRoutingKey(String defaultRoutingKey) {
            this.defaultRoutingKey = defaultRoutingKey;
        }

        public String getDefaultQueue() {
            return defaultQueue;
        }

        public void setDefaultQueue(String defaultQueue) {
            this.defaultQueue = defaultQueue;
        }

        public PublisherProperties getPublisher() {
            return publisher;
        }

        public ListenerProperties getListener() {
            return listener;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public void setProperties(Map<String, String> properties) {
            this.properties = properties;
        }

        /**
         * Properties for RabbitMQ publisher.
         */
        public static class PublisherProperties {
            /**
             * Whether to use publisher confirms.
             */
            private boolean confirms = false;

            /**
             * Whether to use publisher returns.
             */
            private boolean returns = false;

            /**
             * Default exchange type.
             */
            private String exchangeType = "topic";

            /**
             * Whether exchanges should be durable.
             */
            private boolean exchangeDurable = true;

            /**
             * Whether exchanges should be auto-deleted when no longer in use.
             */
            private boolean exchangeAutoDelete = false;

            /**
             * Additional properties for RabbitMQ publisher.
             */
            private Map<String, String> properties = new HashMap<>();

            public boolean isConfirms() {
                return confirms;
            }

            public void setConfirms(boolean confirms) {
                this.confirms = confirms;
            }

            public boolean isReturns() {
                return returns;
            }

            public void setReturns(boolean returns) {
                this.returns = returns;
            }

            public String getExchangeType() {
                return exchangeType;
            }

            public void setExchangeType(String exchangeType) {
                this.exchangeType = exchangeType;
            }

            public boolean isExchangeDurable() {
                return exchangeDurable;
            }

            public void setExchangeDurable(boolean exchangeDurable) {
                this.exchangeDurable = exchangeDurable;
            }

            public boolean isExchangeAutoDelete() {
                return exchangeAutoDelete;
            }

            public void setExchangeAutoDelete(boolean exchangeAutoDelete) {
                this.exchangeAutoDelete = exchangeAutoDelete;
            }

            public Map<String, String> getProperties() {
                return properties;
            }

            public void setProperties(Map<String, String> properties) {
                this.properties = properties;
            }
        }

        /**
         * Properties for RabbitMQ listener.
         */
        public static class ListenerProperties {
            /**
             * Whether to automatically declare queues, exchanges, and bindings.
             */
            private boolean autoDeclare = true;

            /**
             * Whether queues should be durable.
             */
            private boolean queueDurable = true;

            /**
             * Whether queues should be exclusive.
             */
            private boolean queueExclusive = false;

            /**
             * Whether queues should be auto-deleted when no longer in use.
             */
            private boolean queueAutoDelete = false;

            /**
             * Prefetch count for the listener.
             */
            private int prefetchCount = 250;

            /**
             * Default concurrency for the listener.
             */
            private int concurrency = 1;

            /**
             * Maximum concurrency for the listener.
             */
            private int maxConcurrency = 10;

            /**
             * Additional properties for RabbitMQ listener.
             */
            private Map<String, String> properties = new HashMap<>();

            public boolean isAutoDeclare() {
                return autoDeclare;
            }

            public void setAutoDeclare(boolean autoDeclare) {
                this.autoDeclare = autoDeclare;
            }

            public boolean isQueueDurable() {
                return queueDurable;
            }

            public void setQueueDurable(boolean queueDurable) {
                this.queueDurable = queueDurable;
            }

            public boolean isQueueExclusive() {
                return queueExclusive;
            }

            public void setQueueExclusive(boolean queueExclusive) {
                this.queueExclusive = queueExclusive;
            }

            public boolean isQueueAutoDelete() {
                return queueAutoDelete;
            }

            public void setQueueAutoDelete(boolean queueAutoDelete) {
                this.queueAutoDelete = queueAutoDelete;
            }

            public int getPrefetchCount() {
                return prefetchCount;
            }

            public void setPrefetchCount(int prefetchCount) {
                this.prefetchCount = prefetchCount;
            }

            public int getConcurrency() {
                return concurrency;
            }

            public void setConcurrency(int concurrency) {
                this.concurrency = concurrency;
            }

            public int getMaxConcurrency() {
                return maxConcurrency;
            }

            public void setMaxConcurrency(int maxConcurrency) {
                this.maxConcurrency = maxConcurrency;
            }

            public Map<String, String> getProperties() {
                return properties;
            }

            public void setProperties(Map<String, String> properties) {
                this.properties = properties;
            }
        }
    }

    /**
     * Properties for Dapr pub/sub messaging ({@code adhar.messaging.dapr.*}).
     * <p>
     * The Dapr backend itself is activated by the Dapr module's own opt-in property
     * {@code adhar.dapr.enabled=true}; the properties here only configure how the
     * messaging module publishes through Dapr once that backend is active.
     */
    public static class DaprProperties {
        /**
         * Whether to enable the Dapr-backed messaging adapter (in addition to the
         * Dapr module's own {@code adhar.dapr.enabled} switch).
         */
        private boolean enabled = true;

        /**
         * Name of the Dapr pub/sub component to publish through.
         */
        private String pubsubName = "pubsub";

        /**
         * Default topic to use when one is not specified.
         */
        private String defaultTopic = "default-topic";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getPubsubName() {
            return pubsubName;
        }

        public void setPubsubName(String pubsubName) {
            this.pubsubName = pubsubName;
        }

        public String getDefaultTopic() {
            return defaultTopic;
        }

        public void setDefaultTopic(String defaultTopic) {
            this.defaultTopic = defaultTopic;
        }
    }

    /**
     * Common properties for messaging.
     */
    public static class CommonProperties {
        /**
         * Default serialization format.
         */
        private String defaultSerializationFormat = "json";

        /**
         * Whether to enable message tracing.
         */
        private boolean traceEnabled = true;

        /**
         * Whether to enable message validation.
         */
        private boolean validationEnabled = true;

        /**
         * Default timeout for messaging operations in milliseconds.
         */
        private long defaultTimeoutMs = 30000;

        /**
         * Default number of retries for messaging operations.
         */
        private int defaultRetries = 3;

        /**
         * Default backoff period in milliseconds between retries.
         */
        private long defaultBackoffMs = 1000;

        /**
         * Retry configuration applied by {@code RetryingMessageHandler} when consuming
         * messages via {@link com.adhar.kit.messaging.MessagingFacade}.
         */
        private final RetryProperties retry = new RetryProperties();

        /**
         * Dead-letter-queue configuration applied by {@code DeadLetterPublisher} once
         * retries are exhausted.
         */
        private final DlqProperties dlq = new DlqProperties();

        /**
         * Idempotent-consumer (deduplication) configuration.
         */
        private final DedupProperties dedup = new DedupProperties();

        /**
         * Request-reply configuration applied by
         * {@code MessagingFacade#sendAndReceive}.
         */
        private final ReplyProperties reply = new ReplyProperties();

        public RetryProperties getRetry() {
            return retry;
        }

        public DlqProperties getDlq() {
            return dlq;
        }

        public DedupProperties getDedup() {
            return dedup;
        }

        public ReplyProperties getReply() {
            return reply;
        }

        public String getDefaultSerializationFormat() {
            return defaultSerializationFormat;
        }

        public void setDefaultSerializationFormat(String defaultSerializationFormat) {
            this.defaultSerializationFormat = defaultSerializationFormat;
        }

        public boolean isTraceEnabled() {
            return traceEnabled;
        }

        public void setTraceEnabled(boolean traceEnabled) {
            this.traceEnabled = traceEnabled;
        }

        public boolean isValidationEnabled() {
            return validationEnabled;
        }

        public void setValidationEnabled(boolean validationEnabled) {
            this.validationEnabled = validationEnabled;
        }

        public long getDefaultTimeoutMs() {
            return defaultTimeoutMs;
        }

        public void setDefaultTimeoutMs(long defaultTimeoutMs) {
            this.defaultTimeoutMs = defaultTimeoutMs;
        }

        public int getDefaultRetries() {
            return defaultRetries;
        }

        public void setDefaultRetries(int defaultRetries) {
            this.defaultRetries = defaultRetries;
        }

        public long getDefaultBackoffMs() {
            return defaultBackoffMs;
        }

        public void setDefaultBackoffMs(long defaultBackoffMs) {
            this.defaultBackoffMs = defaultBackoffMs;
        }

        /**
         * Configuration for message-handler retry behaviour
         * ({@code adhar.messaging.common.retry.*}).
         */
        public static class RetryProperties {
            /**
             * Whether retry is applied to message consumption via the MessagingFacade.
             * Enabled by default.
             */
            private boolean enabled = true;

            /**
             * Maximum number of handling attempts (including the first attempt) before
             * giving up and routing to the dead-letter queue (if enabled).
             */
            private int maxAttempts = 3;

            /**
             * Delay before the first retry, in milliseconds.
             */
            private long initialDelayMs = 200;

            /**
             * Multiplier applied to the delay after each failed attempt (exponential backoff).
             */
            private double backoffMultiplier = 2.0;

            /**
             * Upper bound for the computed backoff delay, in milliseconds.
             */
            private long maxDelayMs = 5000;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            public long getInitialDelayMs() {
                return initialDelayMs;
            }

            public void setInitialDelayMs(long initialDelayMs) {
                this.initialDelayMs = initialDelayMs;
            }

            public double getBackoffMultiplier() {
                return backoffMultiplier;
            }

            public void setBackoffMultiplier(double backoffMultiplier) {
                this.backoffMultiplier = backoffMultiplier;
            }

            public long getMaxDelayMs() {
                return maxDelayMs;
            }

            public void setMaxDelayMs(long maxDelayMs) {
                this.maxDelayMs = maxDelayMs;
            }
        }

        /**
         * Configuration for dead-letter-queue publishing
         * ({@code adhar.messaging.common.dlq.*}).
         */
        public static class DlqProperties {
            /**
             * Whether failed messages are published to a dead-letter topic once retries
             * (if any) are exhausted. Enabled by default.
             */
            private boolean enabled = true;

            /**
             * Suffix appended to the original destination to compute the dead-letter
             * destination, e.g. {@code order-events} becomes {@code order-events.dlq}.
             */
            private String topicSuffix = ".dlq";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public String getTopicSuffix() {
                return topicSuffix;
            }

            public void setTopicSuffix(String topicSuffix) {
                this.topicSuffix = topicSuffix;
            }
        }

        /**
         * Configuration for idempotent-consumer (message deduplication) support
         * ({@code adhar.messaging.common.dedup.*}). Opt-in: disabled by default.
         */
        public static class DedupProperties {
            /**
             * Whether inbound messages are deduplicated before being handed to the
             * application handler. Disabled by default.
             */
            private boolean enabled = false;

            /**
             * How long a processed message id is remembered before it may be treated as
             * new again, in milliseconds.
             */
            private long ttlMs = 600_000;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public long getTtlMs() {
                return ttlMs;
            }

            public void setTtlMs(long ttlMs) {
                this.ttlMs = ttlMs;
            }
        }

        /**
         * Configuration for the request-reply pattern
         * ({@code adhar.messaging.common.reply.*}).
         */
        public static class ReplyProperties {
            /**
             * How long {@code sendAndReceive} waits for a reply before failing,
             * in milliseconds.
             */
            private long timeoutMs = 30_000;

            /**
             * Suffix appended to the request topic to compute the reply topic used by
             * the correlation-id based (Kafka-style) request-reply flow, e.g.
             * {@code order-queries} replies arrive on {@code order-queries.reply}.
             */
            private String topicSuffix = ".reply";

            public long getTimeoutMs() {
                return timeoutMs;
            }

            public void setTimeoutMs(long timeoutMs) {
                this.timeoutMs = timeoutMs;
            }

            public String getTopicSuffix() {
                return topicSuffix;
            }

            public void setTopicSuffix(String topicSuffix) {
                this.topicSuffix = topicSuffix;
            }
        }
    }

    /**
     * Properties for the transactional outbox ({@code adhar.messaging.outbox.*}).
     * <p>
     * When enabled, an {@code OutboxStore} bean (JDBC-backed when a {@code DataSource}
     * is available, in-memory otherwise) and a scheduled {@code OutboxRelay} are
     * registered, and {@code MessagingFacade#publishViaOutbox} becomes usable.
     */
    public static class OutboxProperties {
        /**
         * Whether the transactional outbox is enabled. Disabled by default.
         */
        private boolean enabled = false;

        /**
         * Interval between relay passes, in milliseconds.
         */
        private long relayIntervalMs = 1000;

        /**
         * Maximum number of entries relayed per pass.
         */
        private int batchSize = 50;

        /**
         * Maximum number of relay attempts before an entry is marked
         * {@code DEAD} and no longer retried.
         */
        private int maxAttempts = 5;

        /**
         * Name of the outbox table used by the JDBC-backed store.
         */
        private String tableName = "adhar_outbox";

        /**
         * Whether the JDBC-backed store creates its table at startup if it does
         * not exist yet.
         */
        private boolean initializeSchema = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getRelayIntervalMs() {
            return relayIntervalMs;
        }

        public void setRelayIntervalMs(long relayIntervalMs) {
            this.relayIntervalMs = relayIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public String getTableName() {
            return tableName;
        }

        public void setTableName(String tableName) {
            this.tableName = tableName;
        }

        public boolean isInitializeSchema() {
            return initializeSchema;
        }

        public void setInitializeSchema(boolean initializeSchema) {
            this.initializeSchema = initializeSchema;
        }
    }
}