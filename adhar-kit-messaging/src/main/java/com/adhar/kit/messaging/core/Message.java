package com.adhar.kit.messaging.core;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CloudEvents-compliant Message for Enterprise Event-Driven Architecture.
 *
 * <p>This class represents a message in the messaging system following the CloudEvents 1.0 specification.
 * It provides a standardized way to describe event data across different messaging systems (Kafka, RabbitMQ)
 * and programming languages, enabling interoperability in cloud-native architectures.</p>
 *
 * <p><b>CloudEvents Standard:</b></p>
 * <p>CloudEvents is a specification for describing event data in a common way. This class implements
 * the required and optional CloudEvents attributes, providing a consistent event format across the
 * enterprise. See <a href="https://cloudevents.io/">https://cloudevents.io/</a> for the full specification.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>CloudEvents Compliant:</b> Full support for CloudEvents 1.0 specification</li>
 *   <li><b>Provider Agnostic:</b> Works with Kafka, RabbitMQ, and other messaging systems</li>
 *   <li><b>Immutable:</b> Thread-safe immutable message representation</li>
 *   <li><b>Type-Safe:</b> Generic payload type for compile-time type safety</li>
 *   <li><b>Metadata Rich:</b> Support for headers, source, type, subject, and schema</li>
 * </ul>
 *
 * <p><b>CloudEvents Attributes:</b></p>
 * <ul>
 *   <li><b>id:</b> Unique identifier for the event (UUID)</li>
 *   <li><b>source:</b> URI identifying the context in which the event occurred</li>
 *   <li><b>specversion:</b> CloudEvents specification version (1.0)</li>
 *   <li><b>type:</b> Type of event (e.g., "com.adhar.order.created")</li>
 *   <li><b>datacontenttype:</b> Content type of the data (e.g., "application/json")</li>
 *   <li><b>dataschema:</b> Optional URI of schema for the data</li>
 *   <li><b>subject:</b> Optional subject of the event in the context of the source</li>
 *   <li><b>time:</b> Timestamp when the event occurred</li>
 *   <li><b>data:</b> The event payload</li>
 * </ul>
 *
 * <p><b>Basic Usage:</b></p>
 * <pre>{@code
 * // Simple message
 * OrderCreatedEvent event = new OrderCreatedEvent(orderId, customerId);
 * Message<OrderCreatedEvent> message = new Message<>(event);
 *
 * // Message with destination
 * Message<OrderCreatedEvent> message = new Message<>(event, "order-events");
 *
 * // Message with partition key
 * Message<OrderCreatedEvent> message = new Message<>(
 *     event,
 *     "order-events",
 *     customerId // Partition key
 * );
 * }</pre>
 *
 * <p><b>CloudEvents-Compliant Message:</b></p>
 * <pre>{@code
 * Message<OrderCreatedEvent> message = Message.<OrderCreatedEvent>builder()
 *     .id(UUID.randomUUID().toString())
 *     .source(URI.create("https://adhar.example.com/orders"))
 *     .type("com.adhar.order.created")
 *     .dataContentType("application/json")
 *     .data(event)
 *     .build();
 * }</pre>
 *
 * <p><b>With Complete Metadata:</b></p>
 * <pre>{@code
 * Message<OrderCreatedEvent> message = Message.<OrderCreatedEvent>builder()
 *     .id(UUID.randomUUID().toString())
 *     .source(URI.create("https://adhar.example.com/orders"))
 *     .type("com.adhar.order.created")
 *     .subject("order-" + orderId)
 *     .dataContentType("application/json")
 *     .dataSchema(URI.create("https://schemas.adhar.com/order-created-v1.json"))
 *     .data(event)
 *     .header("priority", "high")
 *     .header("region", "US-WEST")
 *     .header("correlation-id", correlationId)
 *     .build();
 * }</pre>
 *
 * <p><b>JSON Representation:</b></p>
 * <pre>{@code
 * {
 *   "specversion": "1.0",
 *   "id": "a234-1234-1234",
 *   "source": "https://adhar.example.com/orders",
 *   "type": "com.adhar.order.created",
 *   "datacontenttype": "application/json",
 *   "dataschema": "https://schemas.adhar.com/order-created-v1.json",
 *   "subject": "order-12345",
 *   "time": "2025-11-02T10:30:00Z",
 *   "data": {
 *     "orderId": "12345",
 *     "customerId": "67890",
 *     "amount": 100.50
 *   }
 * }
 * }</pre>
 *
 * <p><b>Immutability and Thread Safety:</b></p>
 * <p>Message instances are immutable - all fields are final and collections are defensive copies.
 * This makes messages thread-safe and suitable for concurrent processing.</p>
 *
 * <p><b>Best Practices:</b></p>
 * <ul>
 *   <li>Use meaningful event types (e.g., "com.adhar.order.created", not "OrderEvent")</li>
 *   <li>Include source URI that identifies the event producer</li>
 *   <li>Use subject for additional context within the source</li>
 *   <li>Include correlation IDs in headers for distributed tracing</li>
 *   <li>Use dataschema for versioned event schemas</li>
 * </ul>
 *
 * @param <T> the type of the message payload (event data)
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see <a href="https://cloudevents.io/">CloudEvents Specification</a>
 */
public class Message<T> {

    /**
     * Unique identifier for the message.
     */
    private final String id;

    /**
     * The message payload.
     */
    private final T payload;

    /**
     * Headers associated with the message.
     */
    private final Map<String, Object> headers;

    /**
     * Timestamp when the message was created.
     */
    private final Instant timestamp;

    /**
     * The destination of the message (topic, exchange, etc.).
     */
    private final String destination;

    /**
     * The routing key or partition key for the message.
     */
    private final String routingKey;
    
    /**
     * The source of the event as a URI.
     */
    private final URI source;
    
    /**
     * The type of the event.
     */
    private final String type;
    
    /**
     * The CloudEvents specification version.
     */
    private final String specVersion;
    
    /**
     * The content type of the data.
     */
    private final String dataContentType;
    
    /**
     * The schema of the data.
     */
    private final URI dataSchema;
    
    /**
     * The subject of the event.
     */
    private final String subject;

    /**
     * Creates a new message with the specified payload.
     *
     * @param payload the message payload
     */
    public Message(T payload) {
        this(payload, null, (String)null);
    }

    /**
     * Creates a new message with the specified payload and destination.
     *
     * @param payload     the message payload
     * @param destination the destination of the message
     */
    public Message(T payload, String destination) {
        this(payload, destination, (String)null);
    }

    /**
     * Creates a new message with the specified payload, destination, and routing key.
     *
     * @param payload     the message payload
     * @param destination the destination of the message
     * @param routingKey  the routing key or partition key for the message
     */
    public Message(T payload, String destination, String routingKey) {
        this(payload, destination, routingKey, Collections.emptyMap());
    }

    /**
     * Creates a new message with the specified payload, destination, and headers.
     *
     * @param payload     the message payload
     * @param destination the destination of the message
     * @param headers     the headers associated with the message
     */
    public Message(T payload, String destination, Map<String, Object> headers) {
        this(payload, destination, null, headers);
    }

    /**
     * Creates a new message with the specified payload, destination, routing key, and headers.
     *
     * @param payload     the message payload
     * @param destination the destination of the message
     * @param routingKey  the routing key or partition key for the message
     * @param headers     the headers associated with the message
     */
    public Message(T payload, String destination, String routingKey, Map<String, Object> headers) {
        this(payload, destination, routingKey, headers, 
             URI.create("urn:adhar:messaging"), 
             payload.getClass().getSimpleName(), 
             "1.0", 
             "application/json", 
             null, 
             null);
    }
    
    /**
     * Creates a new message with the specified payload, destination, routing key, headers, and CloudEvent attributes.
     *
     * @param payload        the message payload
     * @param destination    the destination of the message
     * @param routingKey     the routing key or partition key for the message
     * @param headers        the headers associated with the message
     * @param source         the source of the event
     * @param type           the type of the event
     * @param specVersion    the CloudEvents specification version
     * @param dataContentType the content type of the data
     * @param dataSchema     the schema of the data
     * @param subject        the subject of the event
     */
    public Message(T payload, String destination, String routingKey, Map<String, Object> headers,
                  URI source, String type, String specVersion, String dataContentType, URI dataSchema, String subject) {
        this.id = UUID.randomUUID().toString();
        this.payload = payload;
        this.destination = destination;
        this.routingKey = routingKey;
        this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
        this.timestamp = Instant.now();
        this.source = source != null ? source : URI.create("urn:adhar:messaging");
        this.type = type != null ? type : payload.getClass().getSimpleName();
        this.specVersion = specVersion != null ? specVersion : "1.0";
        this.dataContentType = dataContentType != null ? dataContentType : "application/json";
        this.dataSchema = dataSchema;
        this.subject = subject != null ? subject : routingKey;
    }

    /**
     * Gets the unique identifier for the message.
     *
     * @return the message ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the message payload.
     *
     * @return the payload
     */
    public T getPayload() {
        return payload;
    }

    /**
     * Gets the headers associated with the message.
     *
     * @return an unmodifiable view of the headers
     */
    public Map<String, Object> getHeaders() {
        return Collections.unmodifiableMap(headers);
    }

    /**
     * Gets the timestamp when the message was created.
     *
     * @return the timestamp
     */
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Gets the destination of the message.
     *
     * @return the destination
     */
    public String getDestination() {
        return destination;
    }

    /**
     * Gets the routing key or partition key for the message.
     *
     * @return the routing key
     */
    public String getRoutingKey() {
        return routingKey;
    }
    
    /**
     * Gets the source of the event.
     *
     * @return the source URI
     */
    public URI getSource() {
        return source;
    }
    
    /**
     * Gets the type of the event.
     *
     * @return the event type
     */
    public String getType() {
        return type;
    }
    
    /**
     * Gets the CloudEvents specification version.
     *
     * @return the specification version
     */
    public String getSpecVersion() {
        return specVersion;
    }
    
    /**
     * Gets the content type of the data.
     *
     * @return the data content type
     */
    public String getDataContentType() {
        return dataContentType;
    }
    
    /**
     * Gets the schema of the data.
     *
     * @return the data schema URI
     */
    public URI getDataSchema() {
        return dataSchema;
    }
    
    /**
     * Gets the subject of the event.
     *
     * @return the event subject
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Creates a builder for creating messages.
     *
     * @param <T> the type of the payload
     * @return a new builder
     */
    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    /**
     * Builder for creating messages.
     *
     * @param <T> the type of the payload
     */
    public static class Builder<T> {
        private T payload;
        private String destination;
        private String routingKey;
        private final Map<String, Object> headers = new HashMap<>();
        private URI source;
        private String type;
        private String specVersion;
        private String dataContentType;
        private URI dataSchema;
        private String subject;

        /**
         * Sets the payload for the message.
         *
         * @param payload the payload
         * @return this builder
         */
        public Builder<T> payload(T payload) {
            this.payload = payload;
            return this;
        }

        /**
         * Sets the destination for the message.
         *
         * @param destination the destination
         * @return this builder
         */
        public Builder<T> destination(String destination) {
            this.destination = destination;
            return this;
        }

        /**
         * Sets the routing key for the message.
         *
         * @param routingKey the routing key
         * @return this builder
         */
        public Builder<T> routingKey(String routingKey) {
            this.routingKey = routingKey;
            return this;
        }

        /**
         * Adds a header to the message.
         *
         * @param key   the header key
         * @param value the header value
         * @return this builder
         */
        public Builder<T> header(String key, Object value) {
            this.headers.put(key, value);
            return this;
        }

        /**
         * Adds multiple headers to the message.
         *
         * @param headers the headers to add
         * @return this builder
         */
        public Builder<T> headers(Map<String, Object> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }
        
        /**
         * Sets the source for the message.
         *
         * @param source the source URI
         * @return this builder
         */
        public Builder<T> source(URI source) {
            this.source = source;
            return this;
        }
        
        /**
         * Sets the source for the message.
         *
         * @param source the source URI as a string
         * @return this builder
         */
        public Builder<T> source(String source) {
            this.source = URI.create(source);
            return this;
        }
        
        /**
         * Sets the type for the message.
         *
         * @param type the event type
         * @return this builder
         */
        public Builder<T> type(String type) {
            this.type = type;
            return this;
        }
        
        /**
         * Sets the CloudEvents specification version for the message.
         *
         * @param specVersion the specification version
         * @return this builder
         */
        public Builder<T> specVersion(String specVersion) {
            this.specVersion = specVersion;
            return this;
        }
        
        /**
         * Sets the content type for the message data.
         *
         * @param dataContentType the data content type
         * @return this builder
         */
        public Builder<T> dataContentType(String dataContentType) {
            this.dataContentType = dataContentType;
            return this;
        }
        
        /**
         * Sets the schema for the message data.
         *
         * @param dataSchema the data schema URI
         * @return this builder
         */
        public Builder<T> dataSchema(URI dataSchema) {
            this.dataSchema = dataSchema;
            return this;
        }
        
        /**
         * Sets the schema for the message data.
         *
         * @param dataSchema the data schema URI as a string
         * @return this builder
         */
        public Builder<T> dataSchema(String dataSchema) {
            this.dataSchema = URI.create(dataSchema);
            return this;
        }
        
        /**
         * Sets the subject for the message.
         *
         * @param subject the event subject
         * @return this builder
         */
        public Builder<T> subject(String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * Builds the message.
         *
         * @return a new message with the configured properties
         * @throws IllegalStateException if the payload is null
         */
        public Message<T> build() {
            if (payload == null) {
                throw new IllegalStateException("Payload cannot be null");
            }
            return new Message<>(payload, destination, routingKey, headers, 
                               source, type, specVersion, dataContentType, dataSchema, subject);
        }
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", type='" + type + '\'' +
                ", source='" + source + '\'' +
                ", subject='" + subject + '\'' +
                ", payload=" + payload +
                ", headers=" + headers +
                ", timestamp=" + timestamp +
                ", destination='" + destination + '\'' +
                ", routingKey='" + routingKey + '\'' +
                '}';
    }
}