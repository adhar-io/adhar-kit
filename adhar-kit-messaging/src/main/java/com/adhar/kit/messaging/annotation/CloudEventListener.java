package com.adhar.kit.messaging.annotation;

import java.lang.annotation.*;

/**
 * Marks a method as a CloudEvent listener/handler.
 *
 * <p>This annotation enables declarative CloudEvent consumption without explicit subscription code.
 * Methods annotated with @CloudEventListener automatically subscribe to CloudEvents of specified types
 * and invoke the handler when matching events are received.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Declarative Subscription:</b> Subscribe to CloudEvents via annotation</li>
 *   <li><b>Type Filtering:</b> Filter events by CloudEvent type attribute</li>
 *   <li><b>Source Filtering:</b> Filter events by CloudEvent source attribute</li>
 *   <li><b>Consumer Groups:</b> Configure consumer group for load balancing</li>
 *   <li><b>Automatic Deserialization:</b> CloudEvent data automatically deserialized to method parameter type</li>
 *   <li><b>Error Handling:</b> Configure retry and dead letter queue behavior</li>
 * </ul>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderEventHandler {
 *
 *     @CloudEventListener(
 *         topics = "order-events",
 *         types = "com.adhar.order.created"
 *     )
 *     public void handleOrderCreated(OrderCreatedEvent event) {
 *         log.info("Order created: {}", event.getOrderId());
 *         processOrder(event);
 *     }
 * }
 * }</pre>
 *
 * <p><b>With Consumer Group (Load Balancing):</b></p>
 * <pre>{@code
 * @CloudEventListener(
 *     topics = "order-events",
 *     types = "com.adhar.order.created",
 *     consumerGroup = "order-processor" // All instances share the load
 * )
 * public void handleOrderCreated(OrderCreatedEvent event) {
 *     // Only one instance in the group processes each event
 *     processOrder(event);
 * }
 * }</pre>
 *
 * <p><b>Multiple Event Types:</b></p>
 * <pre>{@code
 * @CloudEventListener(
 *     topics = "order-events",
 *     types = {"com.adhar.order.created", "com.adhar.order.updated"}
 * )
 * public void handleOrderEvent(OrderEvent event) {
 *     // Handles both order created and updated events
 *     processOrderEvent(event);
 * }
 * }</pre>
 *
 * <p><b>Source Filtering:</b></p>
 * <pre>{@code
 * @CloudEventListener(
 *     topics = "events",
 *     types = "com.adhar.order.created",
 *     sources = "https://adhar.example.com/orders" // Only from this source
 * )
 * public void handleOrderCreated(OrderCreatedEvent event) {
 *     processOrder(event);
 * }
 * }</pre>
 *
 * <p><b>Multiple Topics:</b></p>
 * <pre>{@code
 * @CloudEventListener(
 *     topics = {"order-events", "order-events-backup"},
 *     types = "com.adhar.order.created"
 * )
 * public void handleOrderCreated(OrderCreatedEvent event) {
 *     processOrder(event);
 * }
 * }</pre>
 *
 * <p><b>With Full CloudEvent Access:</b></p>
 * <pre>{@code
 * @CloudEventListener(
 *     topics = "order-events",
 *     types = "com.adhar.order.created"
 * )
 * public void handleOrderCreated(CloudEvent cloudEvent) {
 *     // Access full CloudEvent metadata
 *     String id = cloudEvent.getId();
 *     URI source = cloudEvent.getSource();
 *     String type = cloudEvent.getType();
 *
 *     // Extract data
 *     OrderCreatedEvent event = cloudEvent.getData().toObjectNode()
 *         .toPojo(OrderCreatedEvent.class);
 *     processOrder(event);
 * }
 * }</pre>
 *
 * <p><b>With Error Handling:</b></p>
 * <pre>{@code
 * @CloudEventListener(
 *     topics = "order-events",
 *     types = "com.adhar.order.created",
 *     maxRetries = 3,
 *     retryBackoff = 1000,
 *     enableDlq = true
 * )
 * public void handleOrderCreated(OrderCreatedEvent event) {
 *     // If processing fails:
 *     // 1. Retry up to 3 times with 1 second backoff
 *     // 2. If still fails, send to dead letter queue
 *     processOrder(event);
 * }
 * }</pre>
 *
 * <p><b>Conditional Processing:</b></p>
 * <pre>{@code
 * @CloudEventListener(
 *     topics = "order-events",
 *     types = "com.adhar.order.created",
 *     condition = "#event.amount > 1000" // Only process high-value orders
 * )
 * public void handleHighValueOrder(OrderCreatedEvent event) {
 *     processHighValueOrder(event);
 * }
 * }</pre>
 *
 * <p><b>Async Processing:</b></p>
 * <pre>{@code
 * @CloudEventListener(
 *     topics = "order-events",
 *     types = "com.adhar.order.created",
 *     async = true // Process in background thread pool
 * )
 * public void handleOrderCreated(OrderCreatedEvent event) {
 *     // Processed asynchronously, doesn't block message consumption
 *     heavyProcessing(event);
 * }
 * }</pre>
 *
 * <p><b>Method Parameter Options:</b></p>
 * <ul>
 *   <li><b>Event POJO:</b> {@code handleEvent(OrderCreatedEvent event)}</li>
 *   <li><b>CloudEvent:</b> {@code handleEvent(CloudEvent cloudEvent)}</li>
 *   <li><b>Message:</b> {@code handleEvent(Message<OrderCreatedEvent> message)}</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   messaging:
 *     cloud-events:
 *       enabled: true
 *       listeners:
 *         auto-startup: true
 *         default-consumer-group: ${spring.application.name}
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see CloudEventPublisher
 * @see CloudEventHandler
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CloudEventListener {

    /**
     * The topics to subscribe to.
     *
     * <p>Can specify single or multiple topics to listen on.</p>
     *
     * @return array of topic names
     */
    String[] topics();

    /**
     * CloudEvent types to listen for.
     *
     * <p>Only CloudEvents matching these types will be delivered to this handler.
     * Uses CloudEvent 'type' attribute for filtering.</p>
     *
     * <p>Example: "com.adhar.order.created", "com.adhar.payment.processed"</p>
     *
     * @return array of event types to match
     */
    String[] types();

    /**
     * CloudEvent sources to listen for (optional).
     *
     * <p>Only CloudEvents from these sources will be delivered to this handler.
     * Uses CloudEvent 'source' attribute for filtering.</p>
     *
     * <p>If empty, accepts events from any source.</p>
     *
     * @return array of source URIs to match
     */
    String[] sources() default {};

    /**
     * Consumer group ID for load balancing.
     *
     * <p>All instances with same consumer group share the message load.
     * If not specified, uses application name as default.</p>
     *
     * @return consumer group ID
     */
    String consumerGroup() default "";

    /**
     * Condition for processing event (SpEL expression).
     *
     * <p>Event is only processed if this condition evaluates to true.</p>
     * <p>Example: "#event.amount > 1000" → only process high-value events</p>
     *
     * @return the condition expression (supports SpEL)
     */
    String condition() default "";

    /**
     * Maximum number of retry attempts on failure.
     *
     * <p>Default: 0 (no retries)</p>
     *
     * @return maximum retry attempts
     */
    int maxRetries() default 0;

    /**
     * Backoff delay between retries in milliseconds.
     *
     * <p>Default: 1000ms (1 second)</p>
     *
     * @return retry backoff in milliseconds
     */
    long retryBackoff() default 1000;

    /**
     * Whether to send failed messages to dead letter queue.
     *
     * <p>When true, messages that fail after all retries are sent to DLQ.</p>
     * <p>Default: false</p>
     *
     * @return true to enable DLQ
     */
    boolean enableDlq() default false;

    /**
     * Whether to process events asynchronously.
     *
     * <p>When true, events are processed in background thread pool.</p>
     * <p>Default: false (synchronous processing)</p>
     *
     * @return true for async processing
     */
    boolean async() default false;

    /**
     * Auto-start this listener on application startup.
     *
     * <p>When false, listener must be started manually.</p>
     * <p>Default: true</p>
     *
     * @return true to auto-start
     */
    boolean autoStartup() default true;

    /**
     * Order/priority of this listener.
     *
     * <p>Lower values have higher priority.</p>
     * <p>Default: 0</p>
     *
     * @return listener order
     */
    int order() default 0;
}

