package com.adhar.kit.dapr.pubsub;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Exposes the Dapr programmatic subscription endpoint ({@code GET /dapr/subscribe}) and a
 * single dispatch endpoint ({@code POST /dapr/subscribe/**}) that routes incoming CloudEvents
 * to the {@code @DaprSubscribe}/{@code @DaprTopic} handler discovered by
 * {@link DaprSubscriptionRegistrar}.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RestController
public class DaprSubscriptionController {

    private final DaprSubscriptionRegistrar registrar;
    private final DaprEventDispatcher dispatcher;

    public DaprSubscriptionController(DaprSubscriptionRegistrar registrar, DaprEventDispatcher dispatcher) {
        this.registrar = registrar;
        this.dispatcher = dispatcher;
    }

    /**
     * Dapr calls this on startup to discover which topics the app subscribes to.
     *
     * @return the registered subscriptions
     */
    @GetMapping(path = "/dapr/subscribe", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<DaprSubscriptionEntry> subscribe() {
        return registrar.getSubscriptions();
    }

    /**
     * Dapr POSTs CloudEvents here for every registered route. The route is resolved from the
     * request's full path so that arbitrary route slugs (multi-segment or not) all match a
     * single mapping.
     *
     * @param request    the incoming HTTP request (used to resolve the exact route)
     * @param cloudEvent the CloudEvent envelope
     * @return 200 with {@code {"status":"SUCCESS"}} on success, 500 with
     *         {@code {"status":"RETRY"}} on handler failure, or 404 with
     *         {@code {"status":"DROP"}} when no handler is registered for the route
     */
    @PostMapping(path = "/dapr/subscribe/**", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> receiveEvent(HttpServletRequest request,
                                                              @RequestBody(required = false) Map<String, Object> cloudEvent) {
        String route = request.getRequestURI();
        return registrar.findHandler(route)
                .map(handler -> toResponse(dispatcher.dispatch(handler, cloudEvent == null ? Map.of() : cloudEvent)))
                .orElseGet(() -> {
                    log.warn("No Dapr subscription handler registered for route '{}'", route);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", DispatchStatus.DROP.name()));
                });
    }

    private ResponseEntity<Map<String, String>> toResponse(DispatchResult result) {
        HttpStatus status = result.status() == DispatchStatus.SUCCESS ? HttpStatus.OK : HttpStatus.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(status).body(Map.of("status", result.status().name()));
    }
}
