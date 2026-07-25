package com.adhar.kit.messaging.core;

import com.adhar.kit.messaging.metrics.MessagingMetrics;
import com.adhar.kit.messaging.properties.AdharMessagingProperties.CommonProperties.DlqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Publishes messages that failed processing (after retries are exhausted, if retry is
 * enabled) to a dead-letter destination derived from the original destination.
 * <p>
 * The dead-letter destination is {@code <originalDestination><topicSuffix>} (default
 * suffix {@code .dlq}). The published message carries the original payload plus headers
 * describing the failure: the failing exception's class and message, the original
 * destination, and the number of handling attempts made.
 * <p>
 * A message is never forwarded to the DLQ a second time: messages that already carry
 * the {@value #HEADER_DLQ_FLAG} header, or whose destination already ends with the
 * configured DLQ suffix, are dropped (with a warning) rather than re-published, which
 * would otherwise create an unbounded {@code topic.dlq.dlq.dlq...} loop if a DLQ
 * consumer itself is wired with retry+DLQ enabled.
 */
public class DeadLetterPublisher {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterPublisher.class);

    /** Header marking a message as having already been routed through a DLQ. */
    public static final String HEADER_DLQ_FLAG = "x-dlq";
    /** Header carrying the original destination the message failed on. */
    public static final String HEADER_ORIGINAL_DESTINATION = "x-dlq-original-destination";
    /** Header carrying the fully-qualified class name of the failing exception, if any. */
    public static final String HEADER_EXCEPTION_CLASS = "x-dlq-exception-class";
    /** Header carrying the failing exception's message, if any. */
    public static final String HEADER_EXCEPTION_MESSAGE = "x-dlq-exception-message";
    /** Header carrying the number of handling attempts made before giving up. */
    public static final String HEADER_ATTEMPTS = "x-dlq-attempts";

    private final MessagePublisher publisher;
    private final DlqProperties dlqProperties;
    private final MessagingMetrics metrics;

    public DeadLetterPublisher(MessagePublisher publisher, DlqProperties dlqProperties) {
        this(publisher, dlqProperties, null);
    }

    public DeadLetterPublisher(MessagePublisher publisher, DlqProperties dlqProperties, MessagingMetrics metrics) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.dlqProperties = Objects.requireNonNull(dlqProperties, "dlqProperties");
        this.metrics = metrics;
    }

    /**
     * Publishes a failed message to its dead-letter destination.
     *
     * @param context  the context the message was being processed under (used for its destination)
     * @param payload  the message payload
     * @param headers  the original message headers (may be {@code null})
     * @param error    the exception that caused processing to fail, if any
     * @param attempts the number of handling attempts made
     * @param <T>      the payload type
     * @return {@code true} if the message was published to the DLQ, {@code false} if DLQ
     *         publishing is disabled, the loop guard tripped, or the publish itself failed
     */
    public <T> boolean publish(MessageHandler.MessageContext context, T payload,
                                Map<String, Object> headers, Throwable error, int attempts) {
        if (!dlqProperties.isEnabled()) {
            log.debug("DLQ disabled - dropping failed message for destination {}",
                    context != null ? context.getDestination() : "unknown");
            return false;
        }

        String originalDestination = context != null ? context.getDestination() : null;

        if (headers != null && Boolean.parseBoolean(String.valueOf(headers.get(HEADER_DLQ_FLAG)))) {
            log.warn("Refusing to re-publish message from {} to a DLQ again (loop guard: already DLQ'd)",
                    originalDestination);
            return false;
        }

        String suffix = dlqProperties.getTopicSuffix();
        if (originalDestination != null && suffix != null && !suffix.isEmpty()
                && originalDestination.endsWith(suffix)) {
            log.warn("Refusing to publish DLQ destination {} to another DLQ (loop guard: already a DLQ topic)",
                    originalDestination);
            return false;
        }

        String dlqDestination = (originalDestination != null ? originalDestination : "unknown") + suffix;

        Map<String, Object> dlqHeaders = new HashMap<>(headers != null ? headers : Collections.emptyMap());
        dlqHeaders.put(HEADER_DLQ_FLAG, "true");
        dlqHeaders.put(HEADER_ORIGINAL_DESTINATION, String.valueOf(originalDestination));
        dlqHeaders.put(HEADER_ATTEMPTS, String.valueOf(attempts));
        if (error != null) {
            dlqHeaders.put(HEADER_EXCEPTION_CLASS, error.getClass().getName());
            dlqHeaders.put(HEADER_EXCEPTION_MESSAGE, String.valueOf(error.getMessage()));
        }

        boolean published = publisher.publish(dlqDestination, dlqHeaders, payload);
        if (published) {
            log.warn("Routed failed message from {} to DLQ destination {} after {} attempt(s)",
                    originalDestination, dlqDestination, attempts);
            if (metrics != null) {
                metrics.recordDlq(originalDestination);
            }
        } else {
            log.error("Failed to publish message to DLQ destination {}", dlqDestination);
        }
        return published;
    }
}
