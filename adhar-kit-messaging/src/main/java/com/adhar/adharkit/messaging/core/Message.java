package com.adhar.adharkit.messaging.core;

import java.net.URI;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a message in the messaging system, following the CloudEvent specification.
 * <p>
 * This class encapsulates a message with its payload, headers, and metadata.
 * It provides a consistent representation of messages across different messaging systems
 * like Kafka and RabbitMQ, while following the CloudEvent specification.
 * <p>
 * CloudEvents is a specification for describing event data in a common way.
 * See https://cloudevents.io/ for more information.
 *
 * @param <T> the type of the payload
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