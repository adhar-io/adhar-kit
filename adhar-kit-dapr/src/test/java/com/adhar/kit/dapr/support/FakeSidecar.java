package com.adhar.kit.dapr.support;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for the sidecar's HTTP API: records every request and answers with responses queued
 * per path prefix (the last queued response of a prefix keeps answering).
 */
public final class FakeSidecar implements AutoCloseable {

    /** A recorded request. */
    public record Request(String method, String path, String query, Map<String, String> headers, byte[] body) {
        public String bodyAsString() {
            return new String(body, StandardCharsets.UTF_8);
        }

        public String header(String name) {
            return headers.get(name.toLowerCase());
        }
    }

    /** A canned response. */
    public record Response(int status, String body, Map<String, String> headers) {
        public static Response of(int status, String body) {
            return new Response(status, body, Map.of("Content-Type", "application/json"));
        }
    }

    private final java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
    private final HttpServer server;
    private final List<Request> requests = new CopyOnWriteArrayList<>();
    private final Map<String, Deque<Response>> responses = new ConcurrentHashMap<>();

    public FakeSidecar() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            Map<String, String> headers = new LinkedHashMap<>();
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), v.getFirst()));
            String path = exchange.getRequestURI().getRawPath();
            requests.add(new Request(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                    headers, body));
            Response response = next(path);
            response.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
            byte[] out = response.body() == null ? new byte[0] : response.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(response.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) {
                exchange.getResponseBody().write(out);
            }
            exchange.close();
        });
        server.start();
    }

    private Response next(String path) {
        String best = null;
        for (String prefix : responses.keySet()) {
            if (path.startsWith(prefix) && (best == null || prefix.length() > best.length())) {
                best = prefix;
            }
        }
        if (best == null) {
            return Response.of(404, "{\"errorCode\":\"ERR_NOT_FOUND\",\"message\":\"no route " + path + "\"}");
        }
        Deque<Response> queue = responses.get(best);
        synchronized (queue) {
            return queue.size() > 1 ? queue.pollFirst() : queue.peekFirst();
        }
    }

    /** Queues a response for every request whose path starts with {@code pathPrefix}. */
    public FakeSidecar respond(String pathPrefix, Response response) {
        responses.computeIfAbsent(pathPrefix, p -> new ArrayDeque<>()).addLast(response);
        return this;
    }

    public FakeSidecar respond(String pathPrefix, int status, String body) {
        return respond(pathPrefix, Response.of(status, body));
    }

    public List<Request> requests() {
        return List.copyOf(requests);
    }

    public Request lastRequest() {
        return requests.getLast();
    }

    public String endpoint() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** A sidecar connection pointing at this server. */
    public DaprConnectionDetails connection(String appId, String apiToken) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("adhar.dapr.mode", "sidecar");
        values.put("adhar.dapr.http-endpoint", endpoint());
        values.put("adhar.dapr.app-id", appId);
        if (apiToken != null) {
            values.put("adhar.dapr.api-token", apiToken);
        }
        return DaprConnectionDetails.resolve(values::get);
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            server.stop(0);
        }
    }
}
