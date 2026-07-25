package com.adhar.kit.messaging.dedup;

import com.adhar.kit.messaging.core.MessageHandler;
import com.adhar.kit.messaging.metrics.MessagingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;

/**
 * {@link MessageHandler} decorator implementing an idempotent consumer: messages whose
 * id has already been processed (per the backing {@link ProcessedMessageStore}) are
 * skipped rather than handed to the delegate handler a second time.
 * <p>
 * The dedup key is resolved, in order of preference, from:
 * <ol>
 *   <li>the {@code ce-id} header (CloudEvents id, set by
 *       {@link com.adhar.kit.messaging.cloudevents.CloudEventAdapter})</li>
 *   <li>the {@code messageId} header (set by the Kafka/RabbitMQ publishers when
 *       tracing is enabled)</li>
 *   <li>{@link MessageHandler.MessageContext#getMessageId()}</li>
 * </ol>
 * A duplicate is treated as successfully handled (the method returns {@code true}) so
 * that broker-level acknowledgment proceeds normally and the message is not redelivered
 * indefinitely.
 *
 * @param <T> the type of the message payload
 */
public class DeduplicatingMessageHandler<T> implements MessageHandler<T> {

    private static final Logger log = LoggerFactory.getLogger(DeduplicatingMessageHandler.class);

    /** CloudEvents id header, preferred dedup key when present. */
    public static final String HEADER_CE_ID = "ce-id";
    /** Fallback message-id header set by the Kafka/RabbitMQ publishers. */
    public static final String HEADER_MESSAGE_ID = "messageId";

    private final MessageHandler<T> delegate;
    private final ProcessedMessageStore store;
    private final MessagingMetrics metrics;

    public DeduplicatingMessageHandler(MessageHandler<T> delegate, ProcessedMessageStore store) {
        this(delegate, store, null);
    }

    public DeduplicatingMessageHandler(MessageHandler<T> delegate, ProcessedMessageStore store,
                                        MessagingMetrics metrics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.store = Objects.requireNonNull(store, "store");
        this.metrics = metrics;
    }

    @Override
    public boolean handle(T payload, Map<String, Object> headers, MessageContext context) {
        String key = resolveKey(headers, context);
        if (key != null && !store.markIfNotProcessed(key)) {
            log.debug("Skipping duplicate message id {} for destination {}", key,
                    context != null ? context.getDestination() : "unknown");
            if (metrics != null) {
                metrics.recordDuplicate(context != null ? context.getDestination() : null);
            }
            return true;
        }
        return delegate.handle(payload, headers, context);
    }

    private String resolveKey(Map<String, Object> headers, MessageContext context) {
        if (headers != null) {
            Object ceId = headers.get(HEADER_CE_ID);
            if (ceId != null) {
                return ceId.toString();
            }
            Object messageId = headers.get(HEADER_MESSAGE_ID);
            if (messageId != null) {
                return messageId.toString();
            }
        }
        return context != null ? context.getMessageId() : null;
    }
}
