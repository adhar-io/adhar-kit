package com.adhar.kit.messaging;

import com.adhar.kit.messaging.api.MessagingService;
import com.adhar.kit.messaging.core.DeadLetterPublisher;
import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.core.MessageListener;
import com.adhar.kit.messaging.core.MessagePublisher;
import com.adhar.kit.messaging.core.RetryingMessageHandler;
import com.adhar.kit.messaging.core.SimpleMessageContext;
import com.adhar.kit.messaging.dedup.DeduplicatingMessageHandler;
import com.adhar.kit.messaging.dedup.InMemoryProcessedMessageStore;
import com.adhar.kit.messaging.dedup.ProcessedMessageStore;
import com.adhar.kit.messaging.exception.MessagingException;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.outbox.OutboxEntry;
import com.adhar.kit.messaging.outbox.OutboxPayloadCodec;
import com.adhar.kit.messaging.outbox.OutboxStore;
import com.adhar.kit.messaging.properties.AdharMessagingProperties;
import com.adhar.kit.messaging.requestreply.RequestReplyClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Universal Messaging Facade for Event-Driven Microservices Architecture.
 *
 * <p>This facade provides a unified, framework-agnostic messaging API that works seamlessly across
 * different messaging providers (Kafka, RabbitMQ) and application frameworks (Spring Boot, Quarkus, Micronaut).
 * It abstracts the underlying messaging implementation while providing CloudEvents-compliant message handling.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Provider Agnostic:</b> Works with Kafka, RabbitMQ, and other messaging systems</li>
 *   <li><b>Framework Agnostic:</b> Compatible with Spring Boot, Quarkus, and Micronaut</li>
 *   <li><b>CloudEvents Support:</b> Full CloudEvents 1.0 specification compliance</li>
 *   <li><b>Pub-Sub Pattern:</b> Topic-based publish-subscribe messaging</li>
 *   <li><b>Request-Reply:</b> Synchronous RPC over async messaging</li>
 *   <li><b>Consumer Groups:</b> Load balancing and parallel processing</li>
 *   <li><b>Type Safety:</b> Strongly-typed message contracts</li>
 *   <li><b>Reliability:</b> At-least-once delivery guarantees</li>
 * </ul>
 *
 * <p><b>Messaging Patterns Supported:</b></p>
 * <ul>
 *   <li><b>Publish-Subscribe:</b> Broadcast events to multiple subscribers</li>
 *   <li><b>Point-to-Point:</b> Direct message delivery with consumer groups</li>
 *   <li><b>Request-Reply:</b> Synchronous communication over async channels</li>
 * </ul>
 *
 * <p><b>Basic Usage - Publishing:</b></p>
 * <pre>{@code
 * MessagingFacade messaging = MessagingFacade.getInstance();
 *
 * // Simple publish
 * OrderCreatedEvent event = new OrderCreatedEvent(orderId, customerId, amount);
 * messaging.publish("order-events", event);
 *
 * // Publish with partition key (Kafka)
 * messaging.publish("order-events", customerId, event); // Same customer → same partition
 *
 * // Publish CloudEvent
 * Message<OrderCreatedEvent> message = Message.<OrderCreatedEvent>builder()
 *     .id(UUID.randomUUID().toString())
 *     .source(URI.create("https://adhar.example.com/orders"))
 *     .type("com.adhar.order.created")
 *     .data(event)
 *     .build();
 * messaging.publish("order-events", message);
 * }</pre>
 *
 * <p><b>Basic Usage - Subscribing:</b></p>
 * <pre>{@code
 * MessagingFacade messaging = MessagingFacade.getInstance();
 *
 * // Simple subscription
 * messaging.subscribe(
 *     "order-events",
 *     OrderCreatedEvent.class,
 *     event -> {
 *         log.info("Order created: {}", event.getOrderId());
 *         processOrder(event);
 *     }
 * );
 *
 * // Subscription with consumer group (load balancing)
 * messaging.subscribe(
 *     "order-events",
 *     "order-processor-group", // Consumer group ID
 *     OrderCreatedEvent.class,
 *     this::processOrder
 * );
 *
 * // Multiple subscriptions for different event types
 * messaging.subscribe("payment-events", PaymentEvent.class, this::handlePayment);
 * messaging.subscribe("shipment-events", ShipmentEvent.class, this::handleShipment);
 * }</pre>
 *
 * <p><b>Request-Reply Pattern:</b></p>
 * <pre>{@code
 * // Server side - handle requests
 * messaging.subscribe(
 *     "order-queries",
 *     "query-handler",
 *     OrderQueryRequest.class,
 *     request -> {
 *         Order order = orderRepository.findById(request.getOrderId());
 *         return new OrderQueryResponse(order);
 *     }
 * );
 *
 * // Client side - send request and wait for reply
 * OrderQueryRequest request = new OrderQueryRequest(orderId);
 * OrderQueryResponse response = messaging.sendAndReceive(
 *     "order-queries",
 *     request,
 *     OrderQueryResponse.class
 * );
 * }</pre>
 *
 * <p><b>Framework Integration:</b></p>
 *
 * <p><i>Spring Boot:</i></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     @Autowired
 *     private MessagingFacade messaging;
 *
 *     public void createOrder(Order order) {
 *         messaging.publish("order-events", new OrderCreatedEvent(order));
 *     }
 * }
 * }</pre>
 *
 * <p><i>Quarkus:</i></p>
 * <pre>{@code
 * @ApplicationScoped
 * public class OrderService {
 *     @Inject
 *     MessagingFacade messaging;
 *
 *     public void createOrder(Order order) {
 *         messaging.publish("order-events", new OrderCreatedEvent(order));
 *     }
 * }
 * }</pre>
 *
 * <p><i>Micronaut:</i></p>
 * <pre>{@code
 * @Singleton
 * public class OrderService {
 *     @Inject
 *     private MessagingFacade messaging;
 *
 *     public void createOrder(Order order) {
 *         messaging.publish("order-events", new OrderCreatedEvent(order));
 *     }
 * }
 * }</pre>
 *
 * <p><b>Advanced Features:</b></p>
 *
 * <p><i>Transactional Publishing:</i></p>
 * <pre>{@code
 * @Transactional
 * public void createOrderWithEvent(OrderRequest request) {
 *     Order order = orderRepository.save(new Order(request));
 *     messaging.publish("order-events", new OrderCreatedEvent(order));
 *     // Both DB save and event publish succeed or both rollback
 * }
 * }</pre>
 *
 * <p><i>Error Handling:</i></p>
 * <pre>{@code
 * messaging.subscribe("order-events", OrderCreatedEvent.class, event -> {
 *     try {
 *         processOrder(event);
 *     } catch (TransientException e) {
 *         throw e; // Will retry
 *     } catch (PermanentException e) {
 *         sendToDeadLetterQueue(event); // No retry
 *     }
 * });
 * }</pre>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   messaging:
 *     enabled: true
 *     kafka:
 *       enabled: true
 *       bootstrap-servers: kafka1:9092,kafka2:9092
 *       producer:
 *         acks: all
 *         enable-idempotence: true
 *       consumer:
 *         group-id: ${spring.application.name}
 *         enable-auto-commit: false
 * }</pre>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple threads can safely publish and subscribe
 * to messages concurrently.</p>
 *
 * <p><b>Performance Considerations:</b></p>
 * <ul>
 *   <li>Publishing is asynchronous and non-blocking</li>
 *   <li>Consumer groups enable horizontal scaling</li>
 *   <li>Message batching reduces overhead (Kafka)</li>
 *   <li>Connection pooling minimizes latency (RabbitMQ)</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see Message
 * @see MessagingService
 * @see AdharMessagingProperties
 */
public class MessagingFacade implements MessagingService {

    /**
     * SLF4J logger instance for this class.
     */
    private static final Logger log = LoggerFactory.getLogger(MessagingFacade.class);

    /**
     * Singleton instance of the messaging facade.
     * Uses volatile for thread-safe double-checked locking pattern.
     */
    private static volatile MessagingFacade instance;

    /**
     * Registry of active message subscriptions.
     * Maps subscription IDs to their consumer handlers.
     * Thread-safe for concurrent subscription operations.
     */
    private final Map<String, Consumer<?>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Counter for generating unique subscription IDs.
     * Ensures each subscription has a unique identifier.
     */
    private final AtomicInteger subscriptionCounter = new AtomicInteger(0);

    /**
     * Maps a facade-level subscription ID to the consumer ID returned by the underlying
     * {@link MessageListener}, so {@link #unsubscribe(String)} can be delegated correctly.
     * Empty when no {@link MessageListener} is wired (stub mode).
     */
    private final Map<String, String> delegateConsumerIds = new ConcurrentHashMap<>();

    /**
     * The broker-backed publisher this facade delegates to, or {@code null} if none was
     * wired (in which case {@link #publish} falls back to a logging stub).
     */
    private final MessagePublisher messagePublisher;

    /**
     * The broker-backed listener this facade delegates to, or {@code null} if none was
     * wired (in which case {@link #subscribe} stores the handler but never invokes it).
     */
    private final MessageListener messageListener;

    /**
     * Messaging configuration, including the retry/DLQ/dedup settings applied to the
     * consume pipeline built in {@link #buildPipeline(String, Consumer)}.
     */
    private final AdharMessagingProperties properties;

    /**
     * Optional metrics recorder; {@code null} when micrometer is not on the classpath or
     * no {@code MeterRegistry} bean is available.
     */
    private final MessagingMetrics metrics;

    /**
     * Deduplication store shared by every subscription on this facade instance, built
     * only when {@code adhar.messaging.common.dedup.enabled=true}.
     */
    private final ProcessedMessageStore dedupStore;

    /**
     * Dead-letter publisher shared by every subscription on this facade instance, built
     * only when {@code adhar.messaging.common.dlq.enabled=true} and a
     * {@link MessagePublisher} is available to publish to the DLQ destination.
     */
    private final DeadLetterPublisher deadLetterPublisher;

    /**
     * Transactional-outbox store used by {@link #publishViaOutbox}, or {@code null} when the
     * outbox is not enabled (in which case {@link #publishViaOutbox} throws).
     */
    private final OutboxStore outboxStore;

    /**
     * Codec used to serialize payloads persisted via {@link #publishViaOutbox}. Never
     * {@code null} - a default codec is created when none is supplied.
     */
    private final OutboxPayloadCodec outboxCodec;

    /**
     * Broker-specific request-reply client backing {@link #sendAndReceive}, or {@code null}
     * when no broker is configured (in which case {@link #sendAndReceive} throws).
     */
    private final RequestReplyClient requestReplyClient;

    /**
     * Private no-arg constructor used by the legacy {@link #getInstance()} singleton
     * accessor. It builds a fully-stubbed facade (no broker wiring), matching the
     * historical behaviour of this class before broker wiring was introduced.
     *
     * <p>Application code that runs under Spring Boot should instead let
     * {@code MessagingAutoConfiguration} create the {@code MessagingFacade} bean via
     * {@link #MessagingFacade(MessagePublisher, MessageListener, AdharMessagingProperties, MessagingMetrics)},
     * which wires the real Kafka/RabbitMQ publisher and listener beans when present.</p>
     */
    private MessagingFacade() {
        this(null, null, null, null);
    }

    /**
     * Creates a messaging facade backed by the given publisher/listener.
     *
     * <p>Any of the four arguments may be {@code null}:</p>
     * <ul>
     *   <li>a {@code null} {@code messagePublisher} makes {@link #publish} a logging stub</li>
     *   <li>a {@code null} {@code messageListener} makes {@link #subscribe} store the handler
     *       without ever invoking it (a warning is logged)</li>
     *   <li>a {@code null} {@code properties} falls back to default {@link AdharMessagingProperties}</li>
     *   <li>a {@code null} {@code metrics} simply disables metrics recording</li>
     * </ul>
     *
     * @param messagePublisher the publisher to delegate {@link #publish} calls to, or {@code null}
     * @param messageListener  the listener to delegate {@link #subscribe} calls to, or {@code null}
     * @param properties       messaging configuration (retry/DLQ/dedup), or {@code null} for defaults
     * @param metrics          optional metrics recorder, or {@code null} to disable metrics
     */
    public MessagingFacade(MessagePublisher messagePublisher, MessageListener messageListener,
                           AdharMessagingProperties properties, MessagingMetrics metrics) {
        this(messagePublisher, messageListener, properties, metrics, null, null, null);
    }

    /**
     * Creates a fully-featured messaging facade, additionally wiring the transactional
     * outbox ({@link #publishViaOutbox}) and request-reply ({@link #sendAndReceive})
     * capabilities.
     *
     * <p>All arguments are optional (see
     * {@link #MessagingFacade(MessagePublisher, MessageListener, AdharMessagingProperties, MessagingMetrics)}
     * for the first four). Additionally:</p>
     * <ul>
     *   <li>a {@code null} {@code outboxStore} makes {@link #publishViaOutbox} throw a
     *       {@link MessagingException} (outbox not enabled)</li>
     *   <li>a {@code null} {@code outboxCodec} falls back to a default
     *       {@link OutboxPayloadCodec}</li>
     *   <li>a {@code null} {@code requestReplyClient} makes {@link #sendAndReceive} throw a
     *       {@link MessagingException} (no broker configured)</li>
     * </ul>
     *
     * @param messagePublisher   the publisher to delegate {@link #publish} calls to, or {@code null}
     * @param messageListener    the listener to delegate {@link #subscribe} calls to, or {@code null}
     * @param properties         messaging configuration, or {@code null} for defaults
     * @param metrics            optional metrics recorder, or {@code null}
     * @param outboxStore        the transactional-outbox store, or {@code null} if disabled
     * @param outboxCodec        the outbox payload codec, or {@code null} for a default one
     * @param requestReplyClient the request-reply client, or {@code null} if no broker
     */
    public MessagingFacade(MessagePublisher messagePublisher, MessageListener messageListener,
                           AdharMessagingProperties properties, MessagingMetrics metrics,
                           OutboxStore outboxStore, OutboxPayloadCodec outboxCodec,
                           RequestReplyClient requestReplyClient) {
        this.messagePublisher = messagePublisher;
        this.messageListener = messageListener;
        this.properties = properties != null ? properties : new AdharMessagingProperties();
        this.metrics = metrics;
        this.outboxStore = outboxStore;
        this.outboxCodec = outboxCodec != null ? outboxCodec : new OutboxPayloadCodec();
        this.requestReplyClient = requestReplyClient;

        AdharMessagingProperties.CommonProperties common = this.properties.getCommon();
        this.dedupStore = common.getDedup().isEnabled()
                ? new InMemoryProcessedMessageStore(Duration.ofMillis(Math.max(1, common.getDedup().getTtlMs())))
                : null;
        this.deadLetterPublisher = (common.getDlq().isEnabled() && messagePublisher != null)
                ? new DeadLetterPublisher(messagePublisher, common.getDlq(), metrics)
                : null;

        log.info("Initialized MessagingFacade (publisher={}, listener={}, retry.enabled={}, dlq.enabled={}, "
                        + "dedup.enabled={}, outbox={}, requestReply={})",
                describe(messagePublisher), describe(messageListener),
                common.getRetry().isEnabled(), common.getDlq().isEnabled(), common.getDedup().isEnabled(),
                describe(outboxStore), describe(requestReplyClient));
    }

    private static String describe(Object delegate) {
        return delegate != null ? delegate.getClass().getSimpleName() : "none (stub)";
    }

    /**
     * Returns the singleton instance of the messaging facade.
     *
     * <p>This method uses double-checked locking to ensure thread-safe lazy initialization
     * of the singleton instance. The instance is created only once, even when called
     * concurrently from multiple threads.</p>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe and can be called concurrently
     * from multiple threads without synchronization issues.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * MessagingFacade messaging = MessagingFacade.getInstance();
     * messaging.publish("events", new MyEvent());
     * }</pre>
     *
     * @return the singleton MessagingFacade instance
     */
    public static MessagingFacade getInstance() {
        if (instance == null) {
            synchronized (MessagingFacade.class) {
                if (instance == null) {
                    instance = new MessagingFacade();
                }
            }
        }
        return instance;
    }

    /**
     * Publishes a message to the specified topic.
     *
     * <p>This method sends a message to all subscribers of the specified topic. The message
     * will be delivered to all active subscriptions in a publish-subscribe pattern.</p>
     *
     * <p><b>Message Distribution:</b></p>
     * <ul>
     *   <li><b>Kafka:</b> Message is sent to all partitions (round-robin if no key)</li>
     *   <li><b>RabbitMQ:</b> Message is broadcast to all bound queues</li>
     * </ul>
     *
     * <p><b>Delivery Guarantees:</b></p>
     * <ul>
     *   <li><b>At-least-once:</b> Messages may be delivered more than once</li>
     *   <li><b>Ordering:</b> No ordering guarantee without partition key (Kafka)</li>
     *   <li><b>Durability:</b> Messages are persisted based on provider configuration</li>
     * </ul>
     *
     * <p><b>Example - Simple Event Publishing:</b></p>
     * <pre>{@code
     * // Create event
     * OrderCreatedEvent event = new OrderCreatedEvent(
     *     orderId,
     *     customerId,
     *     totalAmount,
     *     Instant.now()
     * );
     *
     * // Publish to all order-events subscribers
     * messaging.publish("order-events", event);
     * }</pre>
     *
     * <p><b>Example - CloudEvent Publishing:</b></p>
     * <pre>{@code
     * Message<OrderCreatedEvent> message = Message.<OrderCreatedEvent>builder()
     *     .id(UUID.randomUUID().toString())
     *     .source(URI.create("https://adhar.example.com/orders"))
     *     .type("com.adhar.order.created")
     *     .dataContentType("application/json")
     *     .data(event)
     *     .build();
     *
     * messaging.publish("order-events", message);
     * }</pre>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe and can be called concurrently.</p>
     *
     * <p><b>Performance:</b> Publishing is typically asynchronous and non-blocking. The method
     * returns immediately while the message is sent in the background.</p>
     *
     * @param <T> the type of the message payload
     * @param topic the topic to publish to (must not be null or empty)
     * @param message the message to publish (must not be null)
     * @throws IllegalArgumentException if topic is null or empty, or message is null
     * @throws MessagingException if the message cannot be published
     * @see #publish(String, String, Object)
     */
    @Override
    public <T> void publish(String topic, T message) {
        log.debug("Publishing message to topic: {}", topic);
        if (messagePublisher == null) {
            throw new MessagingException("No MessagePublisher configured - cannot publish to topic '"
                    + topic + "'. Configure a broker (Kafka, RabbitMQ) or enable Dapr pub/sub "
                    + "(adhar.dapr.enabled=true).");
        }
        boolean published = messagePublisher.publish(topic, message);
        recordPublishMetric(topic, published);
        if (!published) {
            log.warn("Failed to publish message to topic {}", topic);
        }
    }

    /**
     * Publishes a message to the specified topic with a partition/routing key.
     *
     * <p>The key is used for:</p>
     * <ul>
     *   <li><b>Kafka:</b> Partition assignment - messages with same key go to same partition (ordering guarantee)</li>
     *   <li><b>RabbitMQ:</b> Routing key for topic exchanges - determines which queues receive the message</li>
     * </ul>
     *
     * <p><b>Key Benefits:</b></p>
     * <ul>
     *   <li><b>Ordering:</b> Messages with same key are processed in order</li>
     *   <li><b>Locality:</b> Related messages handled by same consumer instance</li>
     *   <li><b>Partitioning:</b> Enables horizontal scaling while maintaining order</li>
     * </ul>
     *
     * <p><b>Example - Customer-based Partitioning:</b></p>
     * <pre>{@code
     * // All events for same customer go to same partition
     * // Ensures order processing is sequential per customer
     * OrderCreatedEvent event = new OrderCreatedEvent(order);
     * messaging.publish(
     *     "order-events",
     *     order.getCustomerId(), // Partition key
     *     event
     * );
     *
     * // Same customer's events processed in order:
     * // 1. Order created
     * // 2. Payment processed
     * // 3. Order shipped
     * }</pre>
     *
     * <p><b>Example - Entity-based Routing:</b></p>
     * <pre>{@code
     * // Route by entity type
     * messaging.publish("events", "order.created", orderEvent);
     * messaging.publish("events", "payment.processed", paymentEvent);
     * messaging.publish("events", "shipment.sent", shipmentEvent);
     *
     * // Different consumers can filter by routing key pattern:
     * // "order.*" - receives all order events
     * // "payment.*" - receives all payment events
     * }</pre>
     *
     * <p><b>Example - Region-based Distribution:</b></p>
     * <pre>{@code
     * // Partition by region for geo-distributed processing
     * String region = order.getShippingAddress().getRegion();
     * messaging.publish("order-events", region, event);
     *
     * // US-WEST orders → US-WEST consumers
     * // EU-CENTRAL orders → EU-CENTRAL consumers
     * }</pre>
     *
     * <p><b>Key Selection Guidelines:</b></p>
     * <ul>
     *   <li>Use customer/user ID for per-user ordering</li>
     *   <li>Use entity ID (order ID, product ID) for entity-specific processing</li>
     *   <li>Use region/tenant ID for partitioned multi-tenancy</li>
     *   <li>Ensure good key distribution to avoid hot partitions</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe.</p>
     *
     * @param <T> the type of the message payload
     * @param topic the topic to publish to (must not be null or empty)
     * @param key the partition/routing key (should not be null for ordering guarantee)
     * @param message the message to publish (must not be null)
     * @throws IllegalArgumentException if topic is null/empty, key is null, or message is null
     * @throws MessagingException if the message cannot be published
     * @see #publish(String, Object)
     */
    @Override
    public <T> void publish(String topic, String key, T message) {
        log.debug("Publishing message with key {} to topic: {}", key, topic);
        if (messagePublisher == null) {
            throw new MessagingException("No MessagePublisher configured - cannot publish to topic '"
                    + topic + "'. Configure a broker (Kafka, RabbitMQ) or enable Dapr pub/sub "
                    + "(adhar.dapr.enabled=true).");
        }
        boolean published = messagePublisher.publish(topic, key, message);
        recordPublishMetric(topic, published);
        if (!published) {
            log.warn("Failed to publish message with key {} to topic {}", key, topic);
        }
    }

    private void recordPublishMetric(String topic, boolean published) {
        if (metrics == null) {
            return;
        }
        if (published) {
            metrics.recordPublish(topic);
        } else {
            metrics.recordPublishFailure(topic);
        }
    }

    /**
     * Publishes a message through the <b>transactional outbox</b> (persist-then-relay).
     *
     * <p>Rather than sending to the broker immediately, the payload is durably persisted as
     * a pending {@link OutboxEntry} in the configured {@link OutboxStore}. When a JDBC-backed
     * store is used and this call runs inside the same database transaction as the business
     * change, the message is committed atomically with that change, eliminating the
     * dual-write problem. A scheduled
     * {@link com.adhar.kit.messaging.outbox.OutboxRelay} then publishes the persisted entry
     * to the broker, retrying on failure.</p>
     *
     * @param <T>         the payload type
     * @param destination the destination (topic/exchange) to eventually publish to
     * @param payload     the payload to persist and later relay (must not be {@code null})
     * @return the id of the persisted outbox entry
     * @throws IllegalArgumentException if {@code destination} is null/blank or {@code payload} is null
     * @throws MessagingException       if the outbox is not enabled (no {@link OutboxStore} wired)
     * @see #publishViaOutbox(String, String, Object)
     */
    public <T> String publishViaOutbox(String destination, T payload) {
        return publishViaOutbox(destination, null, payload);
    }

    /**
     * Publishes a message through the transactional outbox with a partition/routing key.
     *
     * @param <T>         the payload type
     * @param destination the destination (topic/exchange) to eventually publish to
     * @param routingKey  optional partition/routing key applied when the entry is relayed
     * @param payload     the payload to persist and later relay (must not be {@code null})
     * @return the id of the persisted outbox entry
     * @throws IllegalArgumentException if {@code destination} is null/blank or {@code payload} is null
     * @throws MessagingException       if the outbox is not enabled (no {@link OutboxStore} wired)
     * @see #publishViaOutbox(String, Object)
     */
    public <T> String publishViaOutbox(String destination, String routingKey, T payload) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination must not be null or empty");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload must not be null");
        }
        if (outboxStore == null) {
            throw new MessagingException("Transactional outbox is not enabled - set "
                    + "adhar.messaging.outbox.enabled=true (or register an OutboxStore bean)");
        }
        String json = outboxCodec.serialize(payload);
        OutboxEntry entry = OutboxEntry.pending(destination, routingKey, json, payload.getClass().getName());
        outboxStore.save(entry);
        log.debug("Enqueued outbox entry {} for destination {}", entry.getId(), destination);
        return entry.getId();
    }

    /**
     * Subscribes to messages on the specified topic using the default consumer group.
     *
     * <p>This is a convenience method that subscribes using a default consumer group.
     * All instances using this method will be in the same group and share the message load.</p>
     *
     * <p><b>Load Balancing:</b> When multiple instances subscribe with the default group,
     * messages are distributed among them (round-robin or partition-based).</p>
     *
     * <p><b>Example - Simple Event Handler:</b></p>
     * <pre>{@code
     * @Service
     * public class NotificationService {
     *     @Autowired
     *     private MessagingFacade messaging;
     *
     *     @PostConstruct
     *     public void init() {
     *         // Subscribe to order events
     *         messaging.subscribe(
     *             "order-events",
     *             OrderCreatedEvent.class,
     *             event -> {
     *                 log.info("Order created: {}", event.getOrderId());
     *                 sendNotification(event.getCustomerId(), event);
     *             }
     *         );
     *     }
     * }
     * }</pre>
     *
     * <p><b>Example - Multiple Event Types:</b></p>
     * <pre>{@code
     * @PostConstruct
     * public void subscribeToEvents() {
     *     messaging.subscribe("order-events", OrderCreatedEvent.class, this::handleOrderCreated);
     *     messaging.subscribe("payment-events", PaymentEvent.class, this::handlePayment);
     *     messaging.subscribe("shipment-events", ShipmentEvent.class, this::handleShipment);
     * }
     * }</pre>
     *
     * <p><b>Note:</b> For production use, consider using {@link #subscribe(String, String, Class, Consumer)}
     * with an explicit consumer group ID for better control over load balancing.</p>
     *
     * @param <T> the type of messages to consume
     * @param topic the topic to subscribe to (must not be null or empty)
     * @param messageType the class of the message type for deserialization
     * @param handler the consumer that will handle received messages
     * @return a unique subscription ID that can be used to {@link #unsubscribe(String)}
     * @throws IllegalArgumentException if any parameter is null
     * @see #subscribe(String, String, Class, Consumer)
     */
    @Override
    public <T> String subscribe(String topic, Class<T> messageType, Consumer<T> handler) {
        return subscribe(topic, "default-group", messageType, handler);
    }

    /**
     * Subscribes to messages on the specified topic with an explicit consumer group.
     *
     * <p>Consumer groups enable load balancing and parallel processing. All consumers in the same
     * group share the message load - each message is delivered to only one consumer in the group.
     * Multiple groups can subscribe to the same topic - each group receives all messages.</p>
     *
     * <p><b>Consumer Group Semantics:</b></p>
     * <ul>
     *   <li><b>Same Group:</b> Instances share load (each message to one instance)</li>
     *   <li><b>Different Groups:</b> Each group gets all messages (broadcast)</li>
     *   <li><b>Scalability:</b> Add instances to same group for horizontal scaling</li>
     *   <li><b>Isolation:</b> Use different groups for different processing purposes</li>
     * </ul>
     *
     * <p><b>Example - Load Balanced Processing:</b></p>
     * <pre>{@code
     * @Service
     * public class OrderProcessor {
     *     @Autowired
     *     private MessagingFacade messaging;
     *
     *     @PostConstruct
     *     public void subscribe() {
     *         // All instances in "order-processor" group share the load
     *         messaging.subscribe(
     *             "order-events",
     *             "order-processor", // Consumer group
     *             OrderCreatedEvent.class,
     *             this::processOrder
     *         );
     *     }
     *
     *     private void processOrder(OrderCreatedEvent event) {
     *         // Only one instance processes each order
     *         log.info("Processing order: {}", event.getOrderId());
     *         // ... heavy processing ...
     *     }
     * }
     *
     * // Deploy 3 instances:
     * // - Instance 1 processes orders 1, 4, 7, 10...
     * // - Instance 2 processes orders 2, 5, 8, 11...
     * // - Instance 3 processes orders 3, 6, 9, 12...
     * }</pre>
     *
     * <p><b>Example - Multiple Processing Pipelines:</b></p>
     * <pre>{@code
     * // Same topic, different consumer groups for different purposes
     *
     * // Group 1: Analytics (all instances share load)
     * messaging.subscribe("order-events", "analytics-group",
     *     OrderCreatedEvent.class, this::updateAnalytics);
     *
     * // Group 2: Notifications (all instances share load)
     * messaging.subscribe("order-events", "notification-group",
     *     OrderCreatedEvent.class, this::sendNotification);
     *
     * // Group 3: Auditing (all instances share load)
     * messaging.subscribe("order-events", "audit-group",
     *     OrderCreatedEvent.class, this::auditLog);
     *
     * // Each message is processed by ONE instance in EACH group
     * // Total: 3 processing pipelines, all load-balanced
     * }</pre>
     *
     * <p><b>Example - Error Handling:</b></p>
     * <pre>{@code
     * messaging.subscribe("order-events", "order-processor",
     *     OrderCreatedEvent.class,
     *     event -> {
     *         try {
     *             processOrder(event);
     *         } catch (TransientException e) {
     *             log.warn("Transient error, will retry: {}", e.getMessage());
     *             throw e; // Framework will retry
     *         } catch (PermanentException e) {
     *             log.error("Permanent error, sending to DLQ: {}", e.getMessage());
     *             sendToDeadLetterQueue(event);
     *             // Don't throw - acknowledge message
     *         }
     *     }
     * );
     * }</pre>
     *
     * <p><b>Subscription Lifecycle:</b></p>
     * <ul>
     *   <li>Subscription remains active until explicitly {@link #unsubscribe(String) unsubscribed}</li>
     *   <li>Subscription survives across application restarts (offset/position persisted)</li>
     *   <li>Rebalancing occurs when group membership changes</li>
     * </ul>
     *
     * <p><b>Performance Considerations:</b></p>
     * <ul>
     *   <li>Use same group ID across instances for load balancing</li>
     *   <li>Keep handlers fast or process asynchronously</li>
     *   <li>Configure prefetch/batch size for throughput</li>
     *   <li>Monitor consumer lag for scaling decisions</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe. The handler will be invoked from
     * messaging framework threads - ensure handler is thread-safe or synchronizes appropriately.</p>
     *
     * @param <T> the type of messages to consume
     * @param topic the topic to subscribe to (must not be null or empty)
     * @param consumerGroup the consumer group ID for load balancing (must not be null or empty)
     * @param messageType the class of the message type for deserialization
     * @param handler the consumer that will handle received messages (must be thread-safe)
     * @return a unique subscription ID that can be used to {@link #unsubscribe(String)}
     * @throws IllegalArgumentException if any parameter is null or empty
     * @throws MessagingException if subscription cannot be established
     * @see #unsubscribe(String)
     */
    @Override
    public <T> String subscribe(String topic, String consumerGroup,
                                 Class<T> messageType, Consumer<T> handler) {
        String subscriptionId = "sub-" + subscriptionCounter.incrementAndGet();
        subscriptions.put(subscriptionId, handler);

        if (messageListener == null) {
            log.warn("MessagingFacade subscribe not fully implemented - no MessageListener bean registered; "
                    + "handler for topic {} was stored but will never receive messages (using stub)", topic);
            return subscriptionId;
        }

        MessageHandler<T> pipeline = buildPipeline(topic, handler);
        String consumerId = messageListener.subscribeWithHeadersAndAck(topic, consumerGroup, messageType,
                (payload, headers) -> {
                    MessageHandler.MessageContext context = new SimpleMessageContext(
                            topic, topic, consumerGroup, subscriptionId, consumerGroup, resolveMessageId(headers));
                    return pipeline.handle(payload, headers, context);
                });
        delegateConsumerIds.put(subscriptionId, consumerId);

        log.info("Subscribed to topic {} with group {} via {} (subscriptionId: {})",
                 topic, consumerGroup, messageListener.getClass().getSimpleName(), subscriptionId);
        return subscriptionId;
    }

    /**
     * Builds the message-handling pipeline applied to every message delivered to a
     * facade-level subscription: the user's handler at the core, optionally wrapped with
     * retry (+ dead-letter routing on exhaustion), optionally wrapped again with
     * deduplication.
     * <p>
     * Deduplication sits outermost so a duplicate delivery is skipped entirely rather
     * than uselessly retried; retry sits directly around the user handler so only actual
     * processing failures trigger a retry/backoff/DLQ cycle.
     *
     * @param topic   the topic/queue this subscription is consuming from (used for metrics/logging)
     * @param handler the user-supplied handler to invoke for each (non-duplicate) message
     * @param <T>     the message payload type
     * @return the composed handler to invoke for every delivered message
     */
    private <T> MessageHandler<T> buildPipeline(String topic, Consumer<T> handler) {
        MessageHandler<T> pipeline = (payload, headers, context) -> {
            handler.accept(payload);
            if (metrics != null) {
                metrics.recordConsume(topic);
            }
            return true;
        };

        AdharMessagingProperties.CommonProperties common = properties.getCommon();

        if (common.getRetry().isEnabled()) {
            DeadLetterPublisher dlq = common.getDlq().isEnabled() ? deadLetterPublisher : null;
            pipeline = new RetryingMessageHandler<>(pipeline, common.getRetry(), Thread::sleep, dlq, metrics);
        }

        if (common.getDedup().isEnabled() && dedupStore != null) {
            pipeline = new DeduplicatingMessageHandler<>(pipeline, dedupStore, metrics);
        }

        return pipeline;
    }

    private String resolveMessageId(Map<String, Object> headers) {
        if (headers != null) {
            Object ceId = headers.get("ce-id");
            if (ceId != null) {
                return ceId.toString();
            }
            Object messageId = headers.get("messageId");
            if (messageId != null) {
                return messageId.toString();
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Unsubscribes from a previously established subscription.
     *
     * <p>This method removes the subscription and stops receiving messages for the given
     * subscription ID. The subscription ID is returned when calling {@link #subscribe} methods.</p>
     *
     * <p><b>Behavior:</b></p>
     * <ul>
     *   <li>Subscription is immediately removed</li>
     *   <li>No more messages will be delivered to the handler</li>
     *   <li>In-flight messages may still be processed</li>
     *   <li>Consumer group rebalancing may occur (Kafka)</li>
     * </ul>
     *
     * <p><b>Example - Temporary Subscription:</b></p>
     * <pre>{@code
     * // Subscribe temporarily
     * String subscriptionId = messaging.subscribe(
     *     "order-events",
     *     OrderCreatedEvent.class,
     *     this::handleEvent
     * );
     *
     * // Process for some time...
     * Thread.sleep(60000);
     *
     * // Unsubscribe
     * messaging.unsubscribe(subscriptionId);
     * log.info("Stopped receiving order events");
     * }</pre>
     *
     * <p><b>Example - Conditional Subscription:</b></p>
     * <pre>{@code
     * private String subscriptionId;
     *
     * public void enableProcessing() {
     *     if (subscriptionId == null) {
     *         subscriptionId = messaging.subscribe(
     *             "order-events",
     *             "order-processor",
     *             OrderCreatedEvent.class,
     *             this::processOrder
     *         );
     *         log.info("Order processing enabled");
     *     }
     * }
     *
     * public void disableProcessing() {
     *     if (subscriptionId != null) {
     *         messaging.unsubscribe(subscriptionId);
     *         subscriptionId = null;
     *         log.info("Order processing disabled");
     *     }
     * }
     * }</pre>
     *
     * <p><b>Example - Cleanup on Shutdown:</b></p>
     * <pre>{@code
     * @PreDestroy
     * public void cleanup() {
     *     for (String subscriptionId : activeSubscriptions) {
     *         messaging.unsubscribe(subscriptionId);
     *     }
     *     log.info("All subscriptions cleaned up");
     * }
     * }</pre>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe.</p>
     *
     * @param subscriptionId the subscription ID returned from subscribe methods
     * @see #subscribe(String, Class, Consumer)
     * @see #subscribe(String, String, Class, Consumer)
     */
    @Override
    public void unsubscribe(String subscriptionId) {
        Consumer<?> removed = subscriptions.remove(subscriptionId);
        String delegateConsumerId = delegateConsumerIds.remove(subscriptionId);
        if (delegateConsumerId != null && messageListener != null) {
            messageListener.unsubscribe(delegateConsumerId);
        }
        if (removed != null || delegateConsumerId != null) {
            log.info("Unsubscribed: {}", subscriptionId);
        }
    }

    /**
     * Sends a request message and waits for a reply (synchronous RPC over async messaging).
     *
     * <p>This method implements the request-reply pattern, enabling synchronous communication
     * over asynchronous messaging infrastructure. It sends a request, waits for a corresponding
     * reply, and returns the result.</p>
     *
     * <p><b>How It Works:</b></p>
     * <ol>
     *   <li>Client sends request to request topic with reply-to address</li>
     *   <li>Server receives request, processes it, and sends reply to reply-to address</li>
     *   <li>Client receives reply and returns it to caller</li>
     * </ol>
     *
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Query operations (get order details, check inventory)</li>
     *   <li>Validation requests (validate address, check credit)</li>
     *   <li>Synchronous commands that need immediate feedback</li>
     *   <li>Service-to-service RPC where HTTP is not suitable</li>
     * </ul>
     *
     * <p><b>Example - Order Query Service:</b></p>
     * <pre>{@code
     * // Server side - handle queries
     * @Service
     * public class OrderQueryService {
     *     @Autowired
     *     private MessagingFacade messaging;
     *
     *     @PostConstruct
     *     public void setupQueryHandler() {
     *         messaging.subscribe(
     *             "order-queries",
     *             "query-handler",
     *             OrderQueryRequest.class,
     *             this::handleQuery
     *         );
     *     }
     *
     *     private OrderQueryResponse handleQuery(OrderQueryRequest request) {
     *         Order order = orderRepository.findById(request.getOrderId());
     *         return new OrderQueryResponse(order);
     *     }
     * }
     *
     * // Client side - send query and get response
     * @Service
     * public class OrderClient {
     *     @Autowired
     *     private MessagingFacade messaging;
     *
     *     public Order getOrderDetails(String orderId) {
     *         OrderQueryRequest request = new OrderQueryRequest(orderId);
     *
     *         // Send request and wait for reply
     *         OrderQueryResponse response = messaging.sendAndReceive(
     *             "order-queries",
     *             request,
     *             OrderQueryResponse.class
     *         );
     *
     *         return response.getOrder();
     *     }
     * }
     * }</pre>
     *
     * <p><b>Example - Inventory Check:</b></p>
     * <pre>{@code
     * public boolean checkInventory(String productId, int quantity) {
     *     InventoryCheckRequest request = new InventoryCheckRequest(productId, quantity);
     *
     *     InventoryCheckResponse response = messaging.sendAndReceive(
     *         "inventory-checks",
     *         request,
     *         InventoryCheckResponse.class
     *     );
     *
     *     return response.isAvailable();
     * }
     * }</pre>
     *
     * <p><b>Example - With Error Handling:</b></p>
     * <pre>{@code
     * public Optional<Order> getOrderSafely(String orderId) {
     *     try {
     *         OrderQueryRequest request = new OrderQueryRequest(orderId);
     *         OrderQueryResponse response = messaging.sendAndReceive(
     *             "order-queries",
     *             request,
     *             OrderQueryResponse.class
     *         );
     *         return Optional.of(response.getOrder());
     *     } catch (TimeoutException e) {
     *         log.warn("Query timed out for order: {}", orderId);
     *         return Optional.empty();
     *     } catch (MessagingException e) {
     *         log.error("Error querying order: {}", orderId, e);
     *         return Optional.empty();
     *     }
     * }
     * }</pre>
     *
     * <p><b>Example - Validation Service:</b></p>
     * <pre>{@code
     * public ValidationResult validateAddress(Address address) {
     *     AddressValidationRequest request = new AddressValidationRequest(address);
     *
     *     ValidationResponse response = messaging.sendAndReceive(
     *         "address-validation",
     *         request,
     *         ValidationResponse.class
     *     );
     *
     *     return response.getResult();
     * }
     * }</pre>
     *
     * <p><b>Timeout Behavior:</b></p>
     * <ul>
     *   <li>Default timeout is configured in properties (typically 5 seconds)</li>
     *   <li>TimeoutException thrown if no reply within timeout</li>
     *   <li>Request may still be processed even after timeout</li>
     *   <li>Use appropriate timeout for expected response time</li>
     * </ul>
     *
     * <p><b>Performance Considerations:</b></p>
     * <ul>
     *   <li><b>Blocking:</b> This method blocks until reply received or timeout</li>
     *   <li><b>Scalability:</b> Consider async alternatives for high-volume scenarios</li>
     *   <li><b>Timeout:</b> Set realistic timeouts based on expected processing time</li>
     *   <li><b>Circuit Breaker:</b> Consider wrapping calls with circuit breaker pattern</li>
     * </ul>
     *
     * <p><b>Comparison with HTTP:</b></p>
     * <table>
     *   <tr><th>Aspect</th><th>Messaging RPC</th><th>HTTP</th></tr>
     *   <tr><td>Durability</td><td>Messages persisted</td><td>Request lost if service down</td></tr>
     *   <tr><td>Load Balancing</td><td>Consumer groups</td><td>Load balancer required</td></tr>
     *   <tr><td>Decoupling</td><td>Via message broker</td><td>Direct connection</td></tr>
     *   <tr><td>Latency</td><td>Higher (~10-50ms)</td><td>Lower (~1-10ms)</td></tr>
     *   <tr><td>Reliability</td><td>Retries built-in</td><td>Manual retry needed</td></tr>
     * </table>
     *
     * <p><b>When to Use:</b></p>
     * <ul>
     *   <li>✅ Need durability and guaranteed delivery</li>
     *   <li>✅ Service discovery via broker</li>
     *   <li>✅ Load balancing via consumer groups</li>
     *   <li>✅ Decoupling from service endpoints</li>
     *   <li>❌ Need lowest possible latency (use HTTP)</li>
     *   <li>❌ Very high request volume (use async pub-sub)</li>
     * </ul>
     *
     * <p><b>Thread Safety:</b> This method is thread-safe but blocks the calling thread.</p>
     *
     * @param <REQ> the type of the request message
     * @param <REP> the type of the reply message
     * @param topic the topic to send the request to (must not be null or empty)
     * @param request the request message (must not be null)
     * @param replyType the class of the expected reply type for deserialization
     * @return the reply message from the server
     * @throws IllegalArgumentException if any parameter is null
     * @throws TimeoutException if no reply received within timeout period
     * @throws MessagingException if request cannot be sent or other messaging error occurs
     * @see #publish(String, Object)
     * @see #subscribe(String, String, Class, Consumer)
     */
    @Override
    public <REQ, REP> REP sendAndReceive(String topic, REQ request, Class<REP> replyType) {
        if (topic == null || topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be null or empty");
        }
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        if (requestReplyClient == null) {
            throw new MessagingException("Request-reply is not available: no messaging broker "
                    + "(Kafka or RabbitMQ) is configured");
        }
        log.debug("Sending request-reply to topic: {}", topic);
        Duration timeout = Duration.ofMillis(Math.max(1, properties.getCommon().getReply().getTimeoutMs()));
        return requestReplyClient.sendAndReceive(topic, request, replyType, timeout);
    }

    @Override
    public boolean isConnected() {
        // A facade with no publisher cannot deliver anything - report it honestly.
        return messagePublisher != null;
    }

    @Override
    public int getSubscriptionCount() {
        return subscriptions.size();
    }
}

