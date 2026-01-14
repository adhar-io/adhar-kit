package com.adhar.kit.commons.annotation;

import java.lang.annotation.*;

/**
 * Marks a method that publishes a CloudEvent.
 *
 * <p>Automatically publishes method result as a CloudEvent following
 * the CloudEvents v1.0 specification (CNCF standard).</p>
 *
 * <p><b>Example - Simple Event:</b></p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *
 *     @PublishEvent(
 *         eventType = "com.example.order.placed",
 *         source = "https://api.example.com/orders"
 *     )
 *     public Order placeOrder(OrderRequest request) {
 *         Order order = createOrder(request);
 *         // CloudEvent published automatically with order as data
 *         return order;
 *     }
 * }
 * }</pre>
 *
 * <p><b>Example - With Topic (Kafka):</b></p>
 * <pre>{@code
 * @PublishEvent(
 *     eventType = "com.example.payment.processed",
 *     source = "https://payments.example.com",
 *     topic = "payment-events",
 *     async = true
 * )
 * public PaymentResult processPayment(PaymentRequest request) {
 *     return paymentGateway.process(request);
 * }
 * }</pre>
 *
 * <p><b>Example - With Subject:</b></p>
 * <pre>{@code
 * @PublishEvent(
 *     eventType = "com.example.user.updated",
 *     source = "https://users.example.com",
 *     subject = "users/#userId"  // SpEL expression
 * )
 * public User updateUser(String userId, UpdateUserRequest request) {
 *     return userRepository.update(userId, request);
 * }
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see com.adhar.kit.commons.event.CloudEvent
 * @see <a href="https://cloudevents.io/">CloudEvents Specification</a>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublishEvent {

    /**
     * The CloudEvent type (reverse-DNS format). Required.
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>com.example.order.created</li>
     *   <li>com.example.payment.processed</li>
     *   <li>com.example.user.updated</li>
     * </ul>
     */
    String eventType();

    /**
     * The CloudEvent source URI. Required.
     *
     * <p>Identifies the context in which the event happened.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>https://api.example.com/orders</li>
     *   <li>https://payments.example.com</li>
     *   <li>/services/user-service</li>
     * </ul>
     */
    String source();

    /**
     * The CloudEvent subject (SpEL expression). Optional.
     *
     * <p>Describes the subject of the event in the context of the source.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>"orders/" + #orderId</li>
     *   <li>"users/#userId"</li>
     *   <li>#result.getId()</li>
     * </ul>
     */
    String subject() default "";

    /**
     * The event topic/channel for message brokers. Optional.
     *
     * <p>Used for Kafka, RabbitMQ, etc.</p>
     */
    String topic() default "";

    /**
     * Publish asynchronously. Default is true.
     */
    boolean async() default true;

    /**
     * CloudEvent data content type. Optional.
     *
     * <p>Defaults to "application/json".</p>
     */
    String dataContentType() default "application/json";

    /**
     * CloudEvent data schema URI. Optional.
     */
    String dataSchema() default "";

    /**
     * Condition for publishing (SpEL expression). Optional.
     *
     * <p>Event is published only if condition evaluates to true.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *   <li>#result != null</li>
     *   <li>#result.status == 'SUCCESS'</li>
     *   <li>#result.amount > 1000</li>
     * </ul>
     */
    String condition() default "";
}


