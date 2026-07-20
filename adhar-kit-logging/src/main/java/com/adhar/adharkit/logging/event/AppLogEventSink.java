package com.adhar.adharkit.logging.event;

/**
 * Receiver of published {@link AppLogEvent}s.
 *
 * <p>The default sink ({@link Slf4jAppLogEventSink}) writes events as JSON to dedicated SLF4J
 * loggers. Applications can register additional sink beans to ship events elsewhere (Kafka,
 * a database audit table, an SIEM system, metrics, ...). All registered sinks receive every
 * event; a sink failure is isolated and never breaks the application or the other sinks.</p>
 */
@FunctionalInterface
public interface AppLogEventSink {

    /**
     * Handles a published event. The event is already enriched and masked.
     *
     * @param event the event to handle
     */
    void onEvent(AppLogEvent event);
}
