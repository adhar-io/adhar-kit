package com.adhar.kit.messaging.cloudevents;

import com.adhar.kit.messaging.core.Message;
import io.cloudevents.CloudEvent;
import io.cloudevents.core.builder.CloudEventBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for creating and manipulating CloudEvents.
 *
 * <p>This class provides convenient factory methods and builders for creating CloudEvents
 * following the CloudEvents 1.0 specification. It simplifies CloudEvent creation while
 * ensuring compliance with the specification.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>Fluent Builder:</b> Intuitive fluent API for CloudEvent creation</li>
 *   <li><b>Spec Compliance:</b> Ensures CloudEvents 1.0 specification compliance</li>
 *   <li><b>Automatic IDs:</b> Generates unique IDs automatically</li>
 *   <li><b>Timestamp Handling:</b> Automatic timestamp generation</li>
 *   <li><b>Extension Support:</b> Easy addition of custom extensions</li>
 * </ul>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * // Simple CloudEvent
 * CloudEvent event = CloudEventUtils.builder()
 *     .source("https://adhar.example.com/orders")
 *     .type("com.adhar.order.created")
 *     .data(orderEvent)
 *     .build();
 * }</pre>
 *
 * <p><b>With All Attributes:</b></p>
 * <pre>{@code
 * CloudEvent event = CloudEventUtils.builder()
 *     .id(UUID.randomUUID().toString())
 *     .source("https://adhar.example.com/orders")
 *     .type("com.adhar.order.created")
 *     .subject("order-12345")
 *     .dataContentType("application/json")
 *     .dataSchema("https://schemas.adhar.com/order-created-v1.json")
 *     .data(orderEvent)
 *     .extension("priority", "high")
 *     .extension("region", "US-WEST")
 *     .build();
 * }</pre>
 *
 * <p><b>From Message:</b></p>
 * <pre>{@code
 * Message<OrderEvent> message = new Message<>(orderEvent);
 * CloudEvent event = CloudEventUtils.fromMessage(message);
 * }</pre>
 *
 * <p><b>To Message:</b></p>
 * <pre>{@code
 * CloudEvent event = ...;
 * Message<OrderEvent> message = CloudEventUtils.toMessage(event, OrderEvent.class);
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public final class CloudEventUtils {

    private CloudEventUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a new CloudEvent builder.
     *
     * <p>The builder provides a fluent API for creating CloudEvents with all required
     * and optional attributes.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * CloudEvent event = CloudEventUtils.builder()
     *     .source("https://adhar.example.com/orders")
     *     .type("com.adhar.order.created")
     *     .data(orderEvent)
     *     .build();
     * }</pre>
     *
     * @return a new CloudEvent builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a CloudEvent from a Message.
     *
     * <p>Converts an Adhar Message to a CloudEvents-compliant CloudEvent,
     * preserving all metadata and headers.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * Message<OrderEvent> message = new Message<>(orderEvent);
     * CloudEvent event = CloudEventUtils.fromMessage(message);
     * }</pre>
     *
     * @param message the message to convert
     * @param <T> the type of the message payload
     * @return CloudEvent representation of the message
     */
    public static <T> CloudEvent fromMessage(Message<T> message) {
        CloudEventAdapter adapter = new CloudEventAdapter();
        return adapter.toCloudEvent(message);
    }

    /**
     * Creates a Message from a CloudEvent.
     *
     * <p>Converts a CloudEvent to an Adhar Message, preserving all
     * CloudEvent attributes as message headers.</p>
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * CloudEvent event = ...;
     * Message<OrderEvent> message = CloudEventUtils.toMessage(event, OrderEvent.class);
     * }</pre>
     *
     * @param cloudEvent the CloudEvent to convert
     * @param dataType the expected type of the data
     * @param <T> the type of the data
     * @return Message representation of the CloudEvent
     */
    public static <T> Message<T> toMessage(CloudEvent cloudEvent, Class<T> dataType) {
        CloudEventAdapter adapter = new CloudEventAdapter();
        return adapter.fromCloudEvent(cloudEvent);
    }

    /**
     * Checks if a CloudEvent matches a specific type.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * if (CloudEventUtils.isType(event, "com.adhar.order.created")) {
     *     // Handle order created event
     * }
     * }</pre>
     *
     * @param cloudEvent the CloudEvent to check
     * @param type the expected type
     * @return true if the CloudEvent type matches
     */
    public static boolean isType(CloudEvent cloudEvent, String type) {
        return cloudEvent != null && type.equals(cloudEvent.getType());
    }

    /**
     * Checks if a CloudEvent is from a specific source.
     *
     * <p><b>Example:</b></p>
     * <pre>{@code
     * if (CloudEventUtils.isSource(event, "https://adhar.example.com/orders")) {
     *     // Handle event from orders service
     * }
     * }</pre>
     *
     * @param cloudEvent the CloudEvent to check
     * @param source the expected source URI
     * @return true if the CloudEvent source matches
     */
    public static boolean isSource(CloudEvent cloudEvent, URI source) {
        return cloudEvent != null && source.equals(cloudEvent.getSource());
    }

    /**
     * Fluent builder for creating CloudEvents.
     *
     * <p>Provides a convenient API for building CloudEvents with all
     * required and optional attributes.</p>
     */
    public static class Builder {
        private String id = UUID.randomUUID().toString();
        private URI source;
        private String type;
        private String subject;
        private String dataContentType = "application/json";
        private URI dataSchema;
        private Object data;
        private Instant time = Instant.now();
        private final java.util.Map<String, Object> extensions = new java.util.HashMap<>();

        /**
         * Sets the CloudEvent ID.
         *
         * <p>If not set, a random UUID will be generated.</p>
         *
         * @param id the event ID
         * @return this builder
         */
        public Builder id(String id) {
            this.id = id;
            return this;
        }

        /**
         * Sets the CloudEvent source.
         *
         * <p><b>Required.</b> Identifies the context in which the event occurred.</p>
         *
         * @param source the source URI
         * @return this builder
         */
        public Builder source(String source) {
            this.source = URI.create(source);
            return this;
        }

        /**
         * Sets the CloudEvent source.
         *
         * @param source the source URI
         * @return this builder
         */
        public Builder source(URI source) {
            this.source = source;
            return this;
        }

        /**
         * Sets the CloudEvent type.
         *
         * <p><b>Required.</b> Describes the type of event.</p>
         *
         * @param type the event type
         * @return this builder
         */
        public Builder type(String type) {
            this.type = type;
            return this;
        }

        /**
         * Sets the CloudEvent subject.
         *
         * <p>Optional. Describes the subject of the event in the context of the source.</p>
         *
         * @param subject the event subject
         * @return this builder
         */
        public Builder subject(String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * Sets the data content type.
         *
         * <p>Default: "application/json"</p>
         *
         * @param dataContentType the data content type
         * @return this builder
         */
        public Builder dataContentType(String dataContentType) {
            this.dataContentType = dataContentType;
            return this;
        }

        /**
         * Sets the data schema URI.
         *
         * @param dataSchema the data schema URI
         * @return this builder
         */
        public Builder dataSchema(String dataSchema) {
            this.dataSchema = URI.create(dataSchema);
            return this;
        }

        /**
         * Sets the data schema URI.
         *
         * @param dataSchema the data schema URI
         * @return this builder
         */
        public Builder dataSchema(URI dataSchema) {
            this.dataSchema = dataSchema;
            return this;
        }

        /**
         * Sets the CloudEvent data (payload).
         *
         * @param data the event data
         * @return this builder
         */
        public Builder data(Object data) {
            this.data = data;
            return this;
        }

        /**
         * Sets the CloudEvent timestamp.
         *
         * <p>If not set, current time will be used.</p>
         *
         * @param time the event timestamp
         * @return this builder
         */
        public Builder time(Instant time) {
            this.time = time;
            return this;
        }

        /**
         * Adds a CloudEvent extension.
         *
         * <p>Extensions allow custom metadata to be attached to CloudEvents.</p>
         *
         * @param name the extension name
         * @param value the extension value
         * @return this builder
         */
        public Builder extension(String name, Object value) {
            this.extensions.put(name, value);
            return this;
        }

        /**
         * Adds multiple CloudEvent extensions.
         *
         * @param extensions map of extension name-value pairs
         * @return this builder
         */
        public Builder extensions(Map<String, Object> extensions) {
            this.extensions.putAll(extensions);
            return this;
        }

        /**
         * Builds the CloudEvent.
         *
         * @return the constructed CloudEvent
         * @throws IllegalStateException if required attributes are missing
         */
        public CloudEvent build() {
            if (source == null) {
                throw new IllegalStateException("CloudEvent source is required");
            }
            if (type == null) {
                throw new IllegalStateException("CloudEvent type is required");
            }

            CloudEventBuilder builder = CloudEventBuilder.v1()
                    .withId(id)
                    .withSource(source)
                    .withType(type);

            if (subject != null) {
                builder.withSubject(subject);
            }
            if (dataContentType != null) {
                builder.withDataContentType(dataContentType);
            }
            if (dataSchema != null) {
                builder.withDataSchema(dataSchema);
            }
            if (time != null) {
                builder.withTime(time.atOffset(java.time.ZoneOffset.UTC));
            }
            if (data != null) {
                builder.withData(io.cloudevents.core.data.PojoCloudEventData.wrap(data, null));
            }

            extensions.forEach((key, value) -> {
                if (value != null) {
                    builder.withExtension(key, value.toString());
                }
            });

            return builder.build();
        }
    }
}

