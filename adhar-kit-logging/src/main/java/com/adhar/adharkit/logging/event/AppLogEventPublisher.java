package com.adhar.adharkit.logging.event;

import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Objects;

/**
 * Central pipeline for {@link AppLogEvent}s.
 *
 * <p>On {@link #publish(AppLogEvent)} the publisher:</p>
 * <ol>
 *   <li><b>Enriches</b> the event with correlation ID, trace/span IDs, user ID and tenant ID from
 *       the current MDC context (only fields the producer did not set explicitly).</li>
 *   <li><b>Masks</b> the message, error message and metadata via {@link LogDataMasker}.</li>
 *   <li><b>Dispatches</b> the event to every registered {@link AppLogEventSink}. A failing sink is
 *       logged and skipped — event publishing never breaks business code.</li>
 * </ol>
 *
 * <p>Disabled entirely via {@code adhar.logging.events.enabled=false}.</p>
 */
public class AppLogEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(AppLogEventPublisher.class);

    private final AdharLoggingProperties properties;
    private final LogDataMasker masker;
    private final List<AppLogEventSink> sinks;

    /**
     * Creates the publisher.
     *
     * @param properties logging properties
     * @param masker     masker applied to message and metadata
     * @param sinks      sinks receiving each published event
     */
    public AppLogEventPublisher(AdharLoggingProperties properties, LogDataMasker masker,
                                List<AppLogEventSink> sinks) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.masker = Objects.requireNonNull(masker, "masker cannot be null");
        this.sinks = sinks != null ? List.copyOf(sinks) : List.of();
    }

    /**
     * Whether the event pipeline is enabled.
     *
     * @return true when events are published
     */
    public boolean isEnabled() {
        return properties.isEnabled() && properties.getEvents().isEnabled();
    }

    /**
     * Enriches, masks and dispatches the given event to all sinks.
     *
     * @param event the event to publish (null is ignored)
     */
    public void publish(AppLogEvent event) {
        if (event == null || !isEnabled()) {
            return;
        }
        AppLogEvent prepared = prepare(event);
        for (AppLogEventSink sink : sinks) {
            try {
                sink.onEvent(prepared);
            } catch (Exception e) {
                log.warn("AppLogEvent sink {} failed for event {}: {}",
                        sink.getClass().getName(), prepared.getEventId(), e.getMessage());
            }
        }
    }

    private AppLogEvent prepare(AppLogEvent event) {
        AppLogEvent.Builder builder = event.toBuilder();

        if (event.getCorrelationId() == null) {
            builder.correlationId(MDC.get(properties.getMdc().getCorrelationIdField()));
        }
        if (event.getTraceId() == null) {
            builder.traceId(MDC.get(properties.getTracing().getTraceIdField()));
        }
        if (event.getSpanId() == null) {
            builder.spanId(MDC.get(properties.getTracing().getSpanIdField()));
        }
        if (event.getUserId() == null) {
            builder.userId(MDC.get(properties.getMdc().getUserIdField()));
        }
        if (event.getTenantId() == null) {
            builder.tenantId(MDC.get(properties.getMdc().getTenantIdField()));
        }

        if (masker.isEnabled()) {
            builder.message(masker.maskText(event.getMessage()));
            builder.errorMessage(masker.maskText(event.getErrorMessage()));
            if (!event.getMetadata().isEmpty()) {
                builder.replaceMetadata(masker.maskMap(event.getMetadata()));
            }
        }

        return builder.build();
    }
}
