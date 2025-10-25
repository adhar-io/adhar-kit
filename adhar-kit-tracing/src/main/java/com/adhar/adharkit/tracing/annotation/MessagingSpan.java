package com.adhar.adharkit.tracing.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for tracing messaging operations (Kafka, RabbitMQ, etc.).
 * <p>
 * This annotation creates a span specifically designed for messaging operations,
 * automatically adding messaging-related semantic attributes according to OpenTelemetry conventions.
 * </p>
 *
 * <pre>{@code
 * @MessagingSpan(operation = "send", destination = "user.events", destinationType = "topic")
 * public void publishUserEvent(UserEvent event) {
 *     kafkaTemplate.send("user.events", event);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MessagingSpan {

    /**
     * The name of the span. If empty, will be generated from operation and destination.
     *
     * @return the span name
     */
    String value() default "";

    /**
     * The messaging operation (send, receive, process, etc.).
     *
     * @return the messaging operation
     */
    String operation() default "";

    /**
     * The destination name (topic, queue, exchange, etc.).
     *
     * @return the destination name
     */
    String destination() default "";

    /**
     * The destination type (topic, queue, exchange, etc.).
     *
     * @return the destination type
     */
    String destinationType() default "";

    /**
     * The messaging system (kafka, rabbitmq, redis, etc.).
     *
     * @return the messaging system
     */
    String system() default "";

    /**
     * Whether to include message payload in the span.
     *
     * @return true to include payload
     */
    boolean includePayload() default false;

    /**
     * Whether to include message headers in the span.
     *
     * @return true to include headers
     */
    boolean includeHeaders() default false;

    /**
     * Additional tags to add to the span.
     *
     * @return array of tag key-value pairs
     */
    String[] tags() default {};
}
