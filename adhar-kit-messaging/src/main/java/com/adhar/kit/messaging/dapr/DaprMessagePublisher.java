package com.adhar.kit.messaging.dapr;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.messaging.core.MessagePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Implementation of {@link MessagePublisher} for Dapr pub/sub.
 * <p>
 * This class delegates to {@link DaprFacade#publishEvent(String, String, Object, Map)} so that
 * the {@code MessagingFacade} can run on top of any Dapr pub/sub component (Kafka, RabbitMQ,
 * Redis Streams, Azure Service Bus, ...) without a broker-specific client on the classpath.
 * The Dapr sidecar handles broker connectivity, serialization and CloudEvents enveloping.
 *
 * <p><b>Contract mapping (what Dapr supports and what it doesn't):</b></p>
 * <ul>
 *   <li><b>Destination:</b> the SPI {@code destination} maps to the Dapr topic name; the pub/sub
 *       component name is fixed per publisher instance
 *       ({@code adhar.messaging.dapr.pubsub-name}, default {@code "pubsub"}).</li>
 *   <li><b>Routing/partition key:</b> the SPI {@code routingKey} is forwarded as the Dapr
 *       {@code partitionKey} publish metadata. Components with native partitioning (e.g. Kafka)
 *       honor it for per-key ordering; components without partitioning silently ignore it.
 *       Dapr has no equivalent of RabbitMQ topic-exchange routing-key filtering.</li>
 *   <li><b>Headers:</b> SPI headers ({@code Map<String, Object>}) are forwarded as Dapr publish
 *       metadata, which is string-valued - values are converted with {@code String.valueOf} and
 *       {@code null}-valued headers are skipped. CloudEvents attribute headers ({@code ce-id},
 *       {@code ce-type}, {@code ce-source}) are translated to the corresponding
 *       {@code cloudevent.id}/{@code cloudevent.type}/{@code cloudevent.source} metadata keys so
 *       they override the attributes of the CloudEvent envelope the sidecar creates. Binary or
 *       structured (non-string) header values are not supported by Dapr metadata.</li>
 *   <li><b>CloudEvents:</b> the Dapr sidecar automatically wraps every published payload in a
 *       CloudEvents 1.0 envelope, so plain payloads published through this class are delivered
 *       to subscribers as CloudEvents without any extra work.</li>
 *   <li><b>Async publishing:</b> {@link DaprFacade#publishEvent} is blocking, so the
 *       {@code publishAsync} variants execute the same blocking call on the common
 *       {@link java.util.concurrent.ForkJoinPool} rather than using a broker-native async API.</li>
 * </ul>
 *
 * <p><b>Subscribe-side limitation:</b> this module intentionally provides no Dapr-backed
 * {@link com.adhar.kit.messaging.core.MessageListener}. Dapr delivers subscriptions by calling
 * an HTTP endpoint on the application, and the set of subscribed topics is collected by the
 * sidecar at application startup (via the {@code adhar-kit-dapr} module's
 * {@code DaprSubscriptionRegistrar}/{@code @DaprSubscribe} programmatic-subscription support).
 * That model cannot express the {@code MessageListener} SPI's dynamic runtime
 * {@code subscribe}/{@code unsubscribe} contract without coupling this module to the Dapr
 * module's HTTP controller internals, so consumers should subscribe with
 * {@code @DaprSubscribe} from {@code adhar-kit-dapr} instead.</p>
 */
public class DaprMessagePublisher implements MessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(DaprMessagePublisher.class);

    /**
     * Dapr publish metadata key carrying the partition key for components that support
     * partitioning (e.g. Kafka).
     */
    static final String PARTITION_KEY_METADATA = "partitionKey";

    /**
     * SPI header keys translated to Dapr {@code cloudevent.*} publish metadata so they override
     * the corresponding attributes of the sidecar-generated CloudEvent envelope.
     */
    private static final Map<String, String> CLOUD_EVENT_HEADER_MAPPING = Map.of(
            "ce-id", "cloudevent.id",
            "ce-type", "cloudevent.type",
            "ce-source", "cloudevent.source");

    private final DaprFacade daprFacade;
    private final String pubsubName;
    private final String defaultTopic;

    /**
     * Creates a new DaprMessagePublisher using the default topic {@code "default-topic"} for
     * publishes that do not specify a destination.
     *
     * @param daprFacade the Dapr facade to publish through (must not be {@code null})
     * @param pubsubName the name of the Dapr pub/sub component (must not be blank)
     */
    public DaprMessagePublisher(DaprFacade daprFacade, String pubsubName) {
        this(daprFacade, pubsubName, "default-topic");
    }

    /**
     * Creates a new DaprMessagePublisher.
     *
     * @param daprFacade   the Dapr facade to publish through (must not be {@code null})
     * @param pubsubName   the name of the Dapr pub/sub component (must not be blank)
     * @param defaultTopic the topic used when a publish does not specify a destination
     */
    public DaprMessagePublisher(DaprFacade daprFacade, String pubsubName, String defaultTopic) {
        if (daprFacade == null) {
            throw new IllegalArgumentException("daprFacade must not be null");
        }
        if (!StringUtils.hasText(pubsubName)) {
            throw new IllegalArgumentException("pubsubName must not be null or empty");
        }
        this.daprFacade = daprFacade;
        this.pubsubName = pubsubName;
        this.defaultTopic = StringUtils.hasText(defaultTopic) ? defaultTopic : "default-topic";
    }

    @Override
    public <T> boolean publish(T payload) {
        return publish(defaultTopic, payload);
    }

    @Override
    public <T> boolean publish(String destination, T payload) {
        return publishWithStringRouting(destination, null, payload);
    }

    @Override
    public <T> boolean publish(String destination, String routingKey, T payload) {
        return publishWithStringRouting(destination, routingKey, payload);
    }

    @Override
    public <T> boolean publish(String destination, Map<String, Object> headers, T payload) {
        return publishWithMapHeaders(destination, headers, payload);
    }

    @Override
    public <T> boolean publish(String destination, String routingKey, Map<String, Object> headers, T payload) {
        String topic = StringUtils.hasText(destination) ? destination : defaultTopic;
        try {
            Map<String, String> metadata = toMetadata(routingKey, headers);
            daprFacade.publishEvent(pubsubName, topic, payload, metadata);
            log.debug("Published message to Dapr pubsub {} topic {}", pubsubName, topic);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish message to Dapr pubsub {} topic {}", pubsubName, topic, e);
            return false;
        }
    }

    /**
     * Helper method to disambiguate method calls with String routing key.
     */
    private <T> boolean publishWithStringRouting(String destination, String routingKey, T payload) {
        return publish(destination, routingKey, Collections.emptyMap(), payload);
    }

    /**
     * Helper method to disambiguate method calls with Map headers.
     */
    private <T> boolean publishWithMapHeaders(String destination, Map<String, Object> headers, T payload) {
        return publish(destination, null, headers, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(T payload) {
        return publishAsync(defaultTopic, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, T payload) {
        return publishAsyncWithStringRouting(destination, null, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, String routingKey, T payload) {
        return publishAsyncWithStringRouting(destination, routingKey, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, Map<String, Object> headers, T payload) {
        return publishAsyncWithMapHeaders(destination, headers, payload);
    }

    @Override
    public <T> CompletableFuture<Boolean> publishAsync(String destination, String routingKey, Map<String, Object> headers, T payload) {
        return CompletableFuture.supplyAsync(() -> publish(destination, routingKey, headers, payload));
    }

    /**
     * Helper method to disambiguate method calls with String routing key.
     */
    private <T> CompletableFuture<Boolean> publishAsyncWithStringRouting(String destination, String routingKey, T payload) {
        return publishAsync(destination, routingKey, Collections.emptyMap(), payload);
    }

    /**
     * Helper method to disambiguate method calls with Map headers.
     */
    private <T> CompletableFuture<Boolean> publishAsyncWithMapHeaders(String destination, Map<String, Object> headers, T payload) {
        return publishAsync(destination, null, headers, payload);
    }

    /**
     * Converts the SPI routing key and headers into Dapr publish metadata.
     * <p>
     * The routing key becomes the {@code partitionKey} metadata entry; {@code ce-*} CloudEvents
     * attribute headers are translated to their {@code cloudevent.*} metadata equivalents; all
     * other headers are stringified with {@code String.valueOf}. {@code null}-valued headers are
     * skipped because Dapr metadata values must be strings.
     *
     * @param routingKey the partition key, or {@code null}
     * @param headers    the SPI headers, or {@code null}
     * @return the (possibly empty) Dapr publish metadata
     */
    private Map<String, String> toMetadata(String routingKey, Map<String, Object> headers) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (StringUtils.hasText(routingKey)) {
            metadata.put(PARTITION_KEY_METADATA, routingKey);
        }
        if (headers != null) {
            headers.forEach((key, value) -> {
                if (key == null || value == null) {
                    return;
                }
                metadata.put(CLOUD_EVENT_HEADER_MAPPING.getOrDefault(key, key), String.valueOf(value));
            });
        }
        return metadata;
    }
}
