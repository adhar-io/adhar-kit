package com.adhar.adharkit.messaging.properties;

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
     * Configuration for common messaging properties.
     */
    private final CommonProperties common = new CommonProperties();

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

    public CommonProperties getCommon() {
        return common;
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
    }
}