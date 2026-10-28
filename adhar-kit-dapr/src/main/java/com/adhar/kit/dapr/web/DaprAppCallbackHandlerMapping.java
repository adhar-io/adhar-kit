package com.adhar.kit.dapr.web;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.actor.DaprActorCallbacks;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionHandler;
import com.adhar.kit.dapr.pubsub.DaprSubscriptionRegistrar;
import com.adhar.kit.dapr.pubsub.DispatchResult;
import com.adhar.kit.dapr.pubsub.DispatchStatus;
import com.adhar.kit.dapr.serialization.DaprJsonSerializer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.AbstractHandlerMapping;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Serves every callback the Dapr sidecar makes into the application:
 * <ul>
 *   <li>{@code GET /dapr/subscribe} &mdash; the programmatic subscriptions;</li>
 *   <li>{@code POST <route>} &mdash; event deliveries (single and bulk) to the registered
 *       subscribers, answered {@code {"status": "SUCCESS|RETRY|DROP"}};</li>
 *   <li>{@code OPTIONS|POST /<binding>} &mdash; input bindings;</li>
 *   <li>{@code POST /job/<name>} &mdash; Dapr jobs;</li>
 *   <li>{@code GET /dapr/config} and {@code /actors/...} &mdash; the actor runtime, when hosted.</li>
 * </ul>
 *
 * <p>It is a {@link org.springframework.web.servlet.HandlerMapping} consulted before the
 * application's {@code @RequestMapping}s, so it wins the paths it owns without an
 * ambiguous-mapping failure &mdash; in particular over the Dapr SDK's own {@code DaprController}
 * when an application adds {@code dapr-sdk-springboot}, which would otherwise answer
 * {@code /dapr/subscribe} with the SDK's {@code @Topic} methods only. Paths it does not own fall
 * through to the application.</p>
 *
 * <p>When the sidecar was given an app API token ({@code APP_API_TOKEN}), every callback must
 * carry it in {@code dapr-api-token}; other callers get 401.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
@Slf4j
public class DaprAppCallbackHandlerMapping extends AbstractHandlerMapping {

    /** Ahead of {@code RequestMappingHandlerMapping} (order 0). */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 100;

    private static final String JSON = "application/json";
    private static final String BULK_EVENT_TYPE = "com.dapr.event.sent.bulk";

    private final DaprSubscriptionRegistrar subscriptions;
    private final DaprCallbackRegistry callbacks;
    private final DaprActorCallbacks actors;
    private final ObjectMapper mapper;
    private final byte[] appApiToken;

    /**
     * @param subscriptions the subscriptions to advertise and dispatch to
     * @param callbacks     input binding and job handlers, or {@code null}
     * @param actors        the actor runtime, or {@code null} when this app hosts no actors
     * @param serializer    the payload serializer
     * @param appApiToken   the token the sidecar sends ({@code APP_API_TOKEN}), or {@code null}
     */
    public DaprAppCallbackHandlerMapping(DaprSubscriptionRegistrar subscriptions, DaprCallbackRegistry callbacks,
                                         DaprActorCallbacks actors, DaprJsonSerializer serializer,
                                         String appApiToken) {
        this.subscriptions = subscriptions;
        this.callbacks = callbacks;
        this.actors = actors;
        this.mapper = (serializer == null ? new DaprJsonSerializer() : serializer).objectMapper();
        this.appApiToken = appApiToken == null || appApiToken.isBlank() ? null
                : appApiToken.getBytes(StandardCharsets.UTF_8);
        setOrder(ORDER);
        subscriptions.enableHttpDelivery();
    }

    @Override
    protected Object getHandlerInternal(HttpServletRequest request) {
        String path = path(request);
        String method = request.getMethod();
        HttpRequestHandler handler = resolve(method, path);
        if (handler == null) {
            return null;
        }
        if (appApiToken != null && !tokenMatches(request.getHeader(DaprConnectionDetails.API_TOKEN_HEADER))) {
            return (HttpRequestHandler) (req, res) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED,
                    "Dapr app callbacks need the sidecar's app API token");
        }
        return handler;
    }

    private HttpRequestHandler resolve(String method, String path) {
        if ("GET".equals(method) && "/dapr/subscribe".equals(path)) {
            return (req, res) -> writeJson(res, 200, subscriptions.getSubscriptions());
        }
        if ("POST".equals(method)) {
            Optional<DaprSubscriptionHandler> subscriber = subscriptions.findHandler(path);
            if (subscriber.isPresent()) {
                DaprSubscriptionHandler h = subscriber.get();
                return (req, res) -> deliver(h, req, res);
            }
            if (callbacks != null && path.startsWith("/job/")) {
                Optional<DaprCallbackRegistry.CallbackHandler> job = callbacks.job(path.substring("/job/".length()));
                if (job.isPresent()) {
                    DaprCallbackRegistry.CallbackHandler h = job.get();
                    return (req, res) -> callback(h, req, res);
                }
            }
        }
        if (callbacks != null && ("POST".equals(method) || "OPTIONS".equals(method))) {
            Optional<DaprCallbackRegistry.CallbackHandler> binding = callbacks.inputBinding(path);
            if (binding.isPresent()) {
                DaprCallbackRegistry.CallbackHandler h = binding.get();
                // The sidecar probes each input binding with OPTIONS at startup; 2xx means "subscribed".
                return "OPTIONS".equals(method) ? (req, res) -> res.setStatus(200) : (req, res) -> callback(h, req, res);
            }
        }
        if (actors != null) {
            return actorHandler(method, path);
        }
        return null;
    }

    private HttpRequestHandler actorHandler(String method, String path) {
        if ("GET".equals(method) && "/dapr/config".equals(path)) {
            return (req, res) -> writeBytes(res, 200, actors.config());
        }
        if (!path.startsWith("/actors/")) {
            return null;
        }
        String[] parts = path.substring("/actors/".length()).split("/");
        if ("DELETE".equals(method) && parts.length == 2) {
            return (req, res) -> {
                actors.deactivate(parts[0], parts[1]);
                res.setStatus(200);
            };
        }
        if (!"PUT".equals(method) || parts.length < 4 || !"method".equals(parts[2])) {
            return null;
        }
        if (parts.length == 5 && "timer".equals(parts[3])) {
            return (req, res) -> {
                actors.timer(parts[0], parts[1], parts[4], req.getInputStream().readAllBytes());
                res.setStatus(200);
            };
        }
        if (parts.length == 5 && "remind".equals(parts[3])) {
            return (req, res) -> {
                actors.reminder(parts[0], parts[1], parts[4], req.getInputStream().readAllBytes());
                res.setStatus(200);
            };
        }
        if (parts.length == 4) {
            return (req, res) -> writeBytes(res, 200,
                    actors.invoke(parts[0], parts[1], parts[3], req.getInputStream().readAllBytes()));
        }
        return null;
    }

    private void deliver(DaprSubscriptionHandler handler, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        Map<String, Object> json = readObject(body);
        if (json != null && json.get("entries") instanceof List<?> entries
                && (BULK_EVENT_TYPE.equals(json.get("type")) || handler.bulk() != null)) {
            List<Map<String, Object>> statuses = new ArrayList<>();
            for (Object entry : entries) {
                if (!(entry instanceof Map<?, ?> map)) {
                    continue;
                }
                Object event = map.get("event");
                Map<String, Object> envelope = envelopeOf(handler, event, json);
                DispatchResult result = subscriptions.dispatcher().dispatch(handler, envelope);
                Map<String, Object> status = new LinkedHashMap<>();
                status.put("entryId", map.get("entryId"));
                status.put("status", result.status().name());
                statuses.add(status);
            }
            writeJson(response, 200, Map.of("statuses", statuses));
            return;
        }
        Map<String, Object> envelope;
        if (handler.rawPayload() || json == null) {
            envelope = new LinkedHashMap<>();
            envelope.put("data", json != null ? json : textOrJson(body));
            envelope.put("topic", handler.topic());
            envelope.put("pubsubname", handler.pubsubName());
        } else {
            envelope = json;
        }
        DispatchResult result = subscriptions.dispatcher().dispatch(handler, envelope);
        if (result.status() != DispatchStatus.SUCCESS) {
            log.debug("Event {} on {}/{} answered {}", envelope.get("id"), handler.pubsubName(), handler.topic(),
                    result.status());
        }
        // Dapr reads the status from a 200 body; a non-2xx status would make it retry regardless.
        writeJson(response, 200, Map.of("status", result.status().name()));
    }

    private Map<String, Object> envelopeOf(DaprSubscriptionHandler handler, Object event, Map<String, Object> bulk) {
        if (event instanceof Map<?, ?> map && !handler.rawPayload()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = (Map<String, Object>) map;
            return envelope;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("data", event);
        envelope.put("topic", bulk.getOrDefault("topic", handler.topic()));
        envelope.put("pubsubname", bulk.getOrDefault("pubsubname", handler.pubsubName()));
        return envelope;
    }

    private void callback(DaprCallbackRegistry.CallbackHandler handler, HttpServletRequest request,
                          HttpServletResponse response) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        try {
            byte[] result = handler.handle(body, headers(request));
            if (result == null) {
                response.setStatus(200);
            } else {
                writeBytes(response, 200, result);
            }
        } catch (Exception e) {
            log.warn("Dapr callback {} failed", handler.description(), e);
            writeJson(response, 500, Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private Map<String, Object> readObject(byte[] body) {
        if (body.length == 0) {
            return null;
        }
        int first = 0;
        while (first < body.length && Character.isWhitespace(body[first])) {
            first++;
        }
        if (first == body.length || body[first] != '{') {
            return null;
        }
        try {
            return mapper.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException e) {
            return null;
        }
    }

    private Object textOrJson(byte[] body) {
        try {
            return mapper.readValue(body, Object.class);
        } catch (IOException e) {
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private void writeJson(HttpServletResponse response, int status, Object value) throws IOException {
        writeBytes(response, status, mapper.writeValueAsBytes(value));
    }

    private static void writeBytes(HttpServletResponse response, int status, byte[] body) throws IOException {
        response.setStatus(status);
        response.setContentType(JSON);
        if (body != null) {
            response.setContentLength(body.length);
            response.getOutputStream().write(body);
        }
    }

    private static Map<String, String> headers(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (!DaprConnectionDetails.API_TOKEN_HEADER.equalsIgnoreCase(name)) {
                headers.put(name.toLowerCase(Locale.ROOT), request.getHeader(name));
            }
        }
        return headers;
    }

    private boolean tokenMatches(String presented) {
        return presented != null
                && MessageDigest.isEqual(appApiToken, presented.getBytes(StandardCharsets.UTF_8));
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}
