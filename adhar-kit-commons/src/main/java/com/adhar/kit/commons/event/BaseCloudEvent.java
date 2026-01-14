package com.adhar.kit.commons.event;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Base class for all domain events in Adhar platform following CloudEvents specification.
 *
 * <p>This class provides a standard foundation for domain events across all Adhar modules,
 * ensuring CloudEvents 1.0 specification compliance and consistent event metadata.</p>
 *
 * <p><b>Key Features:</b></p>
 * <ul>
 *   <li><b>CloudEvents Compliant:</b> Full CloudEvents 1.0 specification support</li>
 *   <li><b>Automatic ID Generation:</b> Unique event IDs generated automatically</li>
 *   <li><b>Timestamp Tracking:</b> Automatic event timestamp capture</li>
 *   <li><b>Extensible:</b> Support for custom extensions and metadata</li>
 *   <li><b>Immutable:</b> Thread-safe immutable event representation</li>
 * </ul>
 *
 * <p><b>Usage:</b></p>
 * <pre>{@code
 * public class OrderCreatedEvent extends BaseCloudEvent {
 *     private final String orderId;
 *     private final String customerId;
 *     private final BigDecimal amount;
 *
 *     public OrderCreatedEvent(String orderId, String customerId, BigDecimal amount) {
 *         super(
 *             URI.create("https://adhar.example.com/orders"),
 *             "com.adhar.order.created"
 *         );
 *         this.orderId = orderId;
 *         this.customerId = customerId;
 *         this.amount = amount;
 *     }
 *
 *     // Getters...
 * }
 * }</pre>
 *
 * <p><b>With Subject:</b></p>
 * <pre>{@code
 * public class OrderCreatedEvent extends BaseCloudEvent {
 *     public OrderCreatedEvent(Order order) {
 *         super(
 *             URI.create("https://adhar.example.com/orders"),
 *             "com.adhar.order.created",
 *             "order-" + order.getId() // Subject
 *         );
 *         // ...
 *     }
 * }
 * }</pre>
 *
 * <p><b>With Extensions:</b></p>
 * <pre>{@code
 * public class OrderCreatedEvent extends BaseCloudEvent {
 *     public OrderCreatedEvent(Order order) {
 *         super(
 *             URI.create("https://adhar.example.com/orders"),
 *             "com.adhar.order.created"
 *         );
 *         addExtension("priority", "high");
 *         addExtension("region", order.getRegion());
 *         addExtension("customerId", order.getCustomerId());
 *     }
 * }
 * }</pre>
 *
 * <p><b>CloudEvents Attributes:</b></p>
 * <ul>
 *   <li><b>id:</b> Unique event identifier (auto-generated UUID)</li>
 *   <li><b>source:</b> URI identifying event source context</li>
 *   <li><b>specversion:</b> CloudEvents specification version (1.0)</li>
 *   <li><b>type:</b> Event type (reverse domain notation)</li>
 *   <li><b>time:</b> Event timestamp (auto-generated)</li>
 *   <li><b>subject:</b> Optional event subject</li>
 *   <li><b>datacontenttype:</b> Content type (default: application/json)</li>
 *   <li><b>dataschema:</b> Optional schema URI</li>
 * </ul>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 * @see <a href="https://cloudevents.io/">CloudEvents Specification</a>
 */
public abstract class BaseCloudEvent {

    /**
     * CloudEvents specification version.
     */
    private static final String SPEC_VERSION = "1.0";

    /**
     * Unique identifier for this event.
     */
    private final String id;

    /**
     * The source URI identifying the context in which the event occurred.
     */
    private final URI source;

    /**
     * The type of event (e.g., "com.adhar.order.created").
     */
    private final String type;

    /**
     * The subject of the event in the context of the source (optional).
     */
    private final String subject;

    /**
     * The timestamp when the event occurred.
     */
    private final Instant time;

    /**
     * The content type of the data.
     */
    private final String dataContentType;

    /**
     * The schema URI of the data (optional).
     */
    private final URI dataSchema;

    /**
     * CloudEvent extensions (custom attributes).
     */
    private final Map<String, Object> extensions;

    /**
     * Creates a BaseCloudEvent with source and type.
     *
     * @param source the source URI
     * @param type the event type
     */
    protected BaseCloudEvent(URI source, String type) {
        this(source, type, null);
    }

    /**
     * Creates a BaseCloudEvent with source, type, and subject.
     *
     * @param source the source URI
     * @param type the event type
     * @param subject the event subject
     */
    protected BaseCloudEvent(URI source, String type, String subject) {
        this(source, type, subject, "application/json", null);
    }

    /**
     * Creates a BaseCloudEvent with all attributes.
     *
     * @param source the source URI
     * @param type the event type
     * @param subject the event subject
     * @param dataContentType the data content type
     * @param dataSchema the data schema URI
     */
    protected BaseCloudEvent(URI source, String type, String subject,
                        String dataContentType, URI dataSchema) {
        this.id = UUID.randomUUID().toString();
        this.source = source;
        this.type = type;
        this.subject = subject;
        this.time = Instant.now();
        this.dataContentType = dataContentType != null ? dataContentType : "application/json";
        this.dataSchema = dataSchema;
        this.extensions = new HashMap<>();
    }

    /**
     * Gets the CloudEvents specification version.
     *
     * @return the spec version (1.0)
     */
    public String getSpecVersion() {
        return SPEC_VERSION;
    }

    /**
     * Gets the unique event identifier.
     *
     * @return the event ID
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the event source URI.
     *
     * @return the source URI
     */
    public URI getSource() {
        return source;
    }

    /**
     * Gets the event type.
     *
     * @return the event type
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the event subject.
     *
     * @return the subject, or null if not set
     */
    public String getSubject() {
        return subject;
    }

    /**
     * Gets the event timestamp.
     *
     * @return the event timestamp
     */
    public Instant getTime() {
        return time;
    }

    /**
     * Gets the data content type.
     *
     * @return the content type
     */
    public String getDataContentType() {
        return dataContentType;
    }

    /**
     * Gets the data schema URI.
     *
     * @return the schema URI, or null if not set
     */
    public URI getDataSchema() {
        return dataSchema;
    }

    /**
     * Gets all CloudEvent extensions.
     *
     * @return immutable map of extensions
     */
    public Map<String, Object> getExtensions() {
        return Map.copyOf(extensions);
    }

    /**
     * Adds a CloudEvent extension.
     *
     * <p>Extensions allow custom metadata to be attached to events.</p>
     *
     * @param name the extension name
     * @param value the extension value
     */
    protected void addExtension(String name, Object value) {
        if (name != null && value != null) {
            extensions.put(name, value);
        }
    }

    /**
     * Gets a CloudEvent extension value.
     *
     * @param name the extension name
     * @return the extension value, or null if not found
     */
    public Object getExtension(String name) {
        return extensions.get(name);
    }

    @Override
    public String toString() {
        return "CloudEvent{" +
                "id='" + id + '\'' +
                ", source=" + source +
                ", type='" + type + '\'' +
                ", subject='" + subject + '\'' +
                ", time=" + time +
                ", dataContentType='" + dataContentType + '\'' +
                ", dataSchema=" + dataSchema +
                ", extensions=" + extensions +
                '}';
    }
}

