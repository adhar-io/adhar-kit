package com.adhar.kit.health.web;

import com.adhar.kit.health.event.HealthEventBroadcaster;
import com.adhar.kit.health.event.HealthTransition;
import com.adhar.kit.health.event.HealthTransitionJson;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * Server-Sent Events endpoint streaming health-status transitions as JSON.
 *
 * <p>Each subscriber receives one {@code health-transition} event per status change,
 * with a JSON body produced by {@link HealthTransitionJson}. The endpoint path is
 * configurable via {@code adhar.health.events.sse-path} (default {@code /health/events}).</p>
 *
 * <p>Only wired when Spring Web MVC is on the classpath — the auto-configuration gates
 * this bean with {@code @ConditionalOnClass(SseEmitter.class)}.</p>
 *
 * @author Adhar Platform Team
 * @since 1.2.0
 */
@Slf4j
@RestController
public class HealthEventSseController {

    /** SSE event name emitted for each transition. */
    public static final String EVENT_NAME = "health-transition";

    private final HealthEventBroadcaster broadcaster;
    private final long timeoutMillis;

    /**
     * Creates the controller.
     *
     * @param broadcaster   health-event broadcaster to subscribe emitters to
     * @param timeoutMillis SSE connection timeout in milliseconds ({@code 0} = no timeout)
     */
    public HealthEventSseController(HealthEventBroadcaster broadcaster, long timeoutMillis) {
        this.broadcaster = broadcaster;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Opens an SSE stream of health transitions.
     *
     * @return an emitter wired to the broadcaster for the life of the connection
     */
    @GetMapping(path = "${adhar.health.events.sse-path:/health/events}",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = createEmitter();
        Runnable unsubscribe = broadcaster.subscribe(transition -> sendTransition(emitter, transition));
        emitter.onCompletion(unsubscribe);
        emitter.onTimeout(() -> {
            unsubscribe.run();
            emitter.complete();
        });
        emitter.onError(e -> unsubscribe.run());
        log.debug("Opened health-event SSE stream (subscribers={})", broadcaster.subscriberCount());
        return emitter;
    }

    /**
     * Serializes and pushes a transition to a single emitter, completing the emitter
     * with an error if the write fails (typically a disconnected client).
     *
     * @param emitter    target emitter
     * @param transition transition to send
     */
    /**
     * Factory for the per-connection emitter. Overridable to inject a test double.
     *
     * @return a new SSE emitter honoring the configured timeout
     */
    protected SseEmitter createEmitter() {
        return new SseEmitter(timeoutMillis);
    }

    void sendTransition(SseEmitter emitter, HealthTransition transition) {
        try {
            emitter.send(SseEmitter.event()
                    .name(EVENT_NAME)
                    .data(HealthTransitionJson.toJson(transition), MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException e) {
            log.debug("Failed to send health SSE event; completing stream: {}", e.getMessage());
            emitter.completeWithError(e);
        }
    }
}
