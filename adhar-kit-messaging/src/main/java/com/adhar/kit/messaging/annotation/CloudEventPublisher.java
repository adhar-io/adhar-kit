package com.adhar.kit.messaging.annotation;

import java.lang.annotation.*;

/**
 * Marks a method to automatically publish its return value as a CloudEvent.
 *
 * <p>This annotation enables declarative CloudEvent publishing without explicit messaging code.
 * When applied to a method, the framework automatically wraps the return value in a CloudEvent
 * and publishes it to the specified topic.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Automatic Publishing:</b> Return value automatically published as CloudEvent</li>
 *   <li><b>CloudEvents Compliant:</b> Full CloudEvents 1.0 specification support</li>
 *   <li><b>Customizable Metadata:</b> Configure source, type, and subject via SpEL</li>
 *   <li><b>Conditional Publishing:</b> Publish only when condition is met</li>
 *   <li><b>Async Support:</b> Optional asynchronous publishing</li>
 * </ul>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @CloudEventPublisher(topic = "order-events", type = "com.adhar.order.created")
 *     public OrderCreatedEvent createOrder(OrderRequest request) {
 *         Order order = processOrder(request);
 *         return new OrderCreatedEvent(order);
 *         // Automatically published as CloudEvent to "order-events" topic
 *     }
 * }
 * }</pre>
 *
 * <p><b>With Custom Source and Subject:</b></p>
 * <pre>{@code
 * @CloudEventPublisher(
 *     topic = "order-events",
 *     type = "com.adhar.order.created",
 *     source = "https://adhar.example.com/orders",
 *     subject = "#result.orderId" // SpEL expression
 * )
 * public OrderCreatedEvent createOrder(OrderRequest request) {
 *     // Event source will be: https://adhar.example.com/orders
 *     // Event subject will be: order-12345 (from result.orderId)
 *     return new OrderCreatedEvent(order);
 * }
 * }</pre>
 *
 * <p><b>With Partition Key:</b></p>
 * <pre>{@code
 * @CloudEventPublisher(
 *     topic = "order-events",
 *     type = "com.adhar.order.created",
 *     partitionKey = "#result.customerId" // All events for same customer → same partition
 * )
 * public OrderCreatedEvent createOrder(OrderRequest request) {
 *     return new OrderCreatedEvent(order);
 * }
 * }</pre>
 *
 * <p><b>Conditional Publishing:</b></p>
 * <pre>{@code
 * @CloudEventPublisher(
 *     topic = "order-events",
 *     type = "com.adhar.order.created",
 *     condition = "#result.amount > 1000" // Only publish for high-value orders
 * )
 * public OrderCreatedEvent createOrder(OrderRequest request) {
 *     return new OrderCreatedEvent(order);
 * }
 * }</pre>
 *
 * <p><b>Async Publishing:</b></p>
 * <pre>{@code
 * @CloudEventPublisher(
 *     topic = "order-events",
 *     type = "com.adhar.order.created",
 *     async = true // Don't wait for publish to complete
 * )
 * public OrderCreatedEvent createOrder(OrderRequest request) {
 *     return new OrderCreatedEvent(order);
 * }
 * }</pre>
 *
 * <p><b>With Multiple Metadata:</b></p>
 * <pre>{@code
 * @CloudEventPublisher(
 *     topic = "order-events",
 *     type = "com.adhar.order.created",
 *     source = "https://adhar.example.com/orders",
 *     subject = "#result.orderId",
 *     dataContentType = "application/json",
 *     dataSchema = "https://schemas.adhar.com/order-created-v1.json",
 *     partitionKey = "#result.customerId"
 * )
 * public OrderCreatedEvent createOrder(OrderRequest request) {
 *     return new OrderCreatedEvent(order);
 * }
 * }</pre>
 *
 * <p><b>SpEL Expression Support:</b></p>
 * <p>Use Spring Expression Language (SpEL) to dynamically compute values:</p>
 * <ul>
 *   <li><b>#result</b> - Access return value properties (e.g., #result.orderId)</li>
 *   <li><b>#args[0]</b> - Access method arguments</li>
 *   <li><b>#root</b> - Access root context</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <pre>{@code
 * adhar:
 *   messaging:
 *     cloud-events:
 *       enabled: true
 *       default-source: https://adhar.example.com
 *       default-content-type: application/json
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see CloudEventListener
 * @see CloudEventHandler
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CloudEventPublisher {

    /**
     * The topic to publish the CloudEvent to.
     *
     * <p>This is the destination where the event will be published (e.g., Kafka topic, RabbitMQ exchange).</p>
     *
     * @return the topic name
     */
    String topic();

    /**
     * The CloudEvent type attribute.
     *
     * <p>Describes the type of event. Should follow reverse domain name notation.</p>
     * <p>Examples: "com.adhar.order.created", "com.adhar.payment.processed"</p>
     *
     * @return the event type
     */
    String type();

    /**
     * The CloudEvent source attribute.
     *
     * <p>Identifies the context in which the event occurred. Should be a URI.</p>
     * <p>Default: Uses configured default source or generates one from application context.</p>
     *
     * @return the event source URI
     */
    String source() default "";

    /**
     * The CloudEvent subject attribute.
     *
     * <p>Describes the subject of the event in the context of the source. Supports SpEL expressions.</p>
     * <p>Example: "#result.orderId" → "order-12345"</p>
     *
     * @return the event subject (supports SpEL)
     */
    String subject() default "";

    /**
     * The CloudEvent data content type.
     *
     * <p>Describes the content type of the data.</p>
     * <p>Default: "application/json"</p>
     *
     * @return the data content type
     */
    String dataContentType() default "application/json";

    /**
     * The CloudEvent data schema URI.
     *
     * <p>Optional URI pointing to the schema of the data.</p>
     *
     * @return the data schema URI
     */
    String dataSchema() default "";

    /**
     * Partition key for message routing (Kafka) or routing key (RabbitMQ).
     *
     * <p>Supports SpEL expressions to compute key from result or arguments.</p>
     * <p>Example: "#result.customerId" → ensures all events for same customer go to same partition</p>
     *
     * @return the partition/routing key (supports SpEL)
     */
    String partitionKey() default "";

    /**
     * Condition for publishing (SpEL expression).
     *
     * <p>Event is only published if this condition evaluates to true.</p>
     * <p>Example: "#result.amount > 1000" → only publish high-value orders</p>
     *
     * @return the condition expression (supports SpEL)
     */
    String condition() default "";

    /**
     * Whether to publish asynchronously.
     *
     * <p>When true, publishing happens in background and method returns immediately.</p>
     * <p>Default: false (synchronous publishing)</p>
     *
     * @return true for async publishing
     */
    boolean async() default false;

    /**
     * Whether to include method execution metadata in CloudEvent extensions.
     *
     * <p>When true, adds extensions like execution time, class name, method name.</p>
     * <p>Default: false</p>
     *
     * @return true to include execution metadata
     */
    boolean includeExecutionMetadata() default false;
}

