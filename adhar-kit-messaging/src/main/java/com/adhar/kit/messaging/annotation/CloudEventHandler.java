package com.adhar.kit.messaging.annotation;

import java.lang.annotation.*;

/**
 * Marks a class as a CloudEvent handler, enabling automatic registration of CloudEvent listeners.
 *
 * <p>This annotation is used at class level to indicate that the class contains CloudEvent handling methods.
 * It provides common configuration that applies to all {@link CloudEventListener} methods in the class.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Class-Level Configuration:</b> Common settings for all listeners in the class</li>
 *   <li><b>Consumer Group:</b> Default consumer group for all listeners</li>
 *   <li><b>Auto-Discovery:</b> Automatically discovers @CloudEventListener methods</li>
 *   <li><b>Error Handling:</b> Default error handling strategy for all listeners</li>
 * </ul>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * @CloudEventHandler
 * @Service
 * public class OrderEventHandler {
 *
 *     @CloudEventListener(
 *         topics = "order-events",
 *         types = "com.adhar.order.created"
 *     )
 *     public void handleOrderCreated(OrderCreatedEvent event) {
 *         processOrder(event);
 *     }
 *
 *     @CloudEventListener(
 *         topics = "order-events",
 *         types = "com.adhar.order.updated"
 *     )
 *     public void handleOrderUpdated(OrderUpdatedEvent event) {
 *         updateOrder(event);
 *     }
 * }
 * }</pre>
 *
 * <p><b>With Common Consumer Group:</b></p>
 * <pre>{@code
 * @CloudEventHandler(consumerGroup = "order-service")
 * @Service
 * public class OrderEventHandler {
 *     // All listeners in this class use "order-service" consumer group
 *     // unless overridden at method level
 *
 *     @CloudEventListener(
 *         topics = "order-events",
 *         types = "com.adhar.order.created"
 *     )
 *     public void handleOrderCreated(OrderCreatedEvent event) {
 *         processOrder(event);
 *     }
 * }
 * }</pre>
 *
 * <p><b>With Common Error Handling:</b></p>
 * <pre>{@code
 * @CloudEventHandler(
 *     maxRetries = 3,
 *     retryBackoff = 1000,
 *     enableDlq = true
 * )
 * @Service
 * public class OrderEventHandler {
 *     // All listeners inherit these error handling settings
 *
 *     @CloudEventListener(
 *         topics = "order-events",
 *         types = "com.adhar.order.created"
 *     )
 *     public void handleOrderCreated(OrderCreatedEvent event) {
 *         processOrder(event);
 *     }
 * }
 * }</pre>
 *
 * <p><b>Multiple Handlers:</b></p>
 * <pre>{@code
 * // Separate handlers for different event categories
 *
 * @CloudEventHandler(consumerGroup = "order-processor")
 * @Service
 * public class OrderEventHandler {
 *     @CloudEventListener(topics = "order-events", types = "com.adhar.order.*")
 *     public void handleOrderEvent(OrderEvent event) { }
 * }
 *
 * @CloudEventHandler(consumerGroup = "payment-processor")
 * @Service
 * public class PaymentEventHandler {
 *     @CloudEventListener(topics = "payment-events", types = "com.adhar.payment.*")
 *     public void handlePaymentEvent(PaymentEvent event) { }
 * }
 *
 * @CloudEventHandler(consumerGroup = "notification-service")
 * @Service
 * public class NotificationEventHandler {
 *     @CloudEventListener(topics = {"order-events", "payment-events"}, types = "*")
 *     public void handleAllEvents(CloudEvent event) { }
 * }
 * }</pre>
 *
 * <p><b>Configuration Priority:</b></p>
 * <p>Settings are applied in the following priority (highest to lowest):</p>
 * <ol>
 *   <li>@CloudEventListener method-level annotation</li>
 *   <li>@CloudEventHandler class-level annotation</li>
 *   <li>Application configuration (application.yml)</li>
 *   <li>Framework defaults</li>
 * </ol>
 *
 * <p><b>Example - Method Overrides Class:</b></p>
 * <pre>{@code
 * @CloudEventHandler(
 *     consumerGroup = "default-group",
 *     maxRetries = 3
 * )
 * @Service
 * public class OrderEventHandler {
 *
 *     @CloudEventListener(
 *         topics = "order-events",
 *         types = "com.adhar.order.created"
 *     )
 *     public void handleOrderCreated(OrderCreatedEvent event) {
 *         // Uses: consumerGroup="default-group", maxRetries=3
 *     }
 *
 *     @CloudEventListener(
 *         topics = "order-events",
 *         types = "com.adhar.order.updated",
 *         consumerGroup = "update-processor", // Overrides class-level
 *         maxRetries = 5 // Overrides class-level
 *     )
 *     public void handleOrderUpdated(OrderUpdatedEvent event) {
 *         // Uses: consumerGroup="update-processor", maxRetries=5
 *     }
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see CloudEventListener
 * @see CloudEventPublisher
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CloudEventHandler {

    /**
     * Default consumer group ID for all listeners in this class.
     *
     * <p>Can be overridden by individual @CloudEventListener methods.</p>
     * <p>If not specified, uses application name.</p>
     *
     * @return consumer group ID
     */
    String consumerGroup() default "";

    /**
     * Default maximum retry attempts for all listeners.
     *
     * <p>Can be overridden by individual @CloudEventListener methods.</p>
     * <p>Default: 0 (no retries)</p>
     *
     * @return maximum retry attempts
     */
    int maxRetries() default 0;

    /**
     * Default retry backoff delay in milliseconds.
     *
     * <p>Can be overridden by individual @CloudEventListener methods.</p>
     * <p>Default: 1000ms (1 second)</p>
     *
     * @return retry backoff in milliseconds
     */
    long retryBackoff() default 1000;

    /**
     * Default dead letter queue setting for all listeners.
     *
     * <p>Can be overridden by individual @CloudEventListener methods.</p>
     * <p>Default: false</p>
     *
     * @return true to enable DLQ
     */
    boolean enableDlq() default false;

    /**
     * Default async processing setting for all listeners.
     *
     * <p>Can be overridden by individual @CloudEventListener methods.</p>
     * <p>Default: false (synchronous)</p>
     *
     * @return true for async processing
     */
    boolean async() default false;

    /**
     * Auto-start all listeners in this handler on application startup.
     *
     * <p>Default: true</p>
     *
     * @return true to auto-start
     */
    boolean autoStartup() default true;

    /**
     * Order/priority of this handler.
     *
     * <p>Lower values have higher priority.</p>
     * <p>Default: 0</p>
     *
     * @return handler order
     */
    int order() default 0;
}

