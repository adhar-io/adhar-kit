package com.adhar.kit.dapr.http;

import com.adhar.kit.dapr.DaprConnectionDetails;
import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.observation.DaprTraceContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Minimal client for the sidecar's HTTP API ({@code /v1.0/...}) on the JDK's own
 * {@link HttpClient}: no Spring, no extra dependency, a per-request timeout, the API token and the
 * caller's trace context on every request.
 *
 * <p>The kit uses it for what the SDK's gRPC client does not cover or covers through deprecated
 * calls: service invocation (every verb, headers, query), actor calls, output-binding response
 * metadata, the sidecar health probe, metadata and shutdown.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public class DaprHttpClient {

    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();
    private static final String USER_AGENT = "adhar-kit-java";

    private final HttpClient client;
    private final String endpoint;
    private final String apiToken;
    private final Duration defaultTimeout;
    private final DaprTraceContext traceContext;

    public DaprHttpClient(DaprConnectionDetails connection, Duration defaultTimeout, DaprTraceContext traceContext) {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .version(HttpClient.Version.HTTP_1_1)
                        .build(),
                connection.httpEndpoint(), connection.apiToken(), defaultTimeout, traceContext);
    }

    public DaprHttpClient(HttpClient client, String endpoint, String apiToken, Duration defaultTimeout,
                          DaprTraceContext traceContext) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint must not be null");
        this.apiToken = apiToken;
        this.defaultTimeout = defaultTimeout == null ? Duration.ofSeconds(60) : defaultTimeout;
        this.traceContext = traceContext == null ? DaprTraceContext.NONE : traceContext;
    }

    /** The sidecar base URL. */
    public String endpoint() {
        return endpoint;
    }

    /**
     * Sends a request. Non-2xx responses are returned, not thrown; transport failures (connection
     * refused, timeout) are thrown as {@link DaprError}.
     *
     * @param method      the HTTP method
     * @param path        the path below the endpoint, already encoded (see {@link #encodePath})
     * @param query       query parameters, may be empty
     * @param headers     extra headers, may be empty
     * @param body        the body, or {@code null}
     * @param contentType the body content type, or {@code null}
     * @param timeout     the timeout, or {@code null} for the default
     * @return the response
     */
    public DaprHttpResponse send(String method, String path, Map<String, List<String>> query,
                                 Map<String, String> headers, byte[] body, String contentType, Duration timeout) {
        URI uri = URI.create(endpoint + (path.startsWith("/") ? path : "/" + path) + queryString(query));
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout != null ? timeout : defaultTimeout)
                .header("User-Agent", USER_AGENT);
        if (apiToken != null && !apiToken.isBlank()) {
            builder.header(DaprConnectionDetails.API_TOKEN_HEADER, apiToken);
        }
        Map<String, String> outgoing = new LinkedHashMap<>();
        traceContext.current().forEach(outgoing::put);
        if (headers != null) {
            outgoing.putAll(headers);
        }
        outgoing.forEach((name, value) -> {
            if (value != null && !isRestricted(name)) {
                builder.header(name, value);
            }
        });
        if (body != null && contentType != null && (headers == null || !containsIgnoreCase(headers, "Content-Type"))) {
            builder.header("Content-Type", contentType);
        }
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(body));
        try {
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            Map<String, List<String>> responseHeaders = new LinkedHashMap<>();
            response.headers().map().forEach((name, values) ->
                    responseHeaders.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
            return new DaprHttpResponse(response.statusCode(), responseHeaders, response.body());
        } catch (IOException e) {
            throw DaprError.from("Dapr sidecar request " + method + " " + path + " failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw DaprError.from("Dapr sidecar request " + method + " " + path + " was interrupted", e);
        }
    }

    /** GET with no body. */
    public DaprHttpResponse get(String path, Duration timeout) {
        return send("GET", path, Map.of(), Map.of(), null, null, timeout);
    }

    /**
     * Builds the error for a non-2xx response, reading Dapr's error body
     * ({@code {"errorCode": "...", "message": "...", "details": [...]}}) when there is one.
     *
     * @param operation what the kit was doing
     * @param response  the response
     * @return the error
     */
    public static DaprError toError(String operation, DaprHttpResponse response) {
        String errorCode = null;
        String message = null;
        Map<String, Object> details = new LinkedHashMap<>();
        String text = response.bodyAsString().trim();
        if (text.startsWith("{")) {
            try {
                JsonNode json = ERROR_MAPPER.readTree(text);
                errorCode = textOrNull(json, "errorCode");
                message = textOrNull(json, "message");
                if (message == null) {
                    // RFC 9457 problem+json from an adhar-kit service behind the sidecar.
                    message = textOrNull(json, "detail");
                    if (message == null) {
                        message = textOrNull(json, "title");
                    }
                }
                JsonNode detailNode = json.get("details");
                if (detailNode != null && !detailNode.isNull()) {
                    details.put("details", ERROR_MAPPER.convertValue(detailNode, Object.class));
                }
            } catch (IOException ignored) {
                // Not JSON after all; the raw body is still on the error.
            }
        }
        if (message == null && !text.isEmpty() && text.length() <= 512) {
            message = text;
        }
        String full = operation + ": HTTP " + response.status() + (message == null ? "" : " " + message);
        return new DaprError(full, response.status(), errorCode != null ? errorCode : "HTTP_" + response.status(),
                message, details, response.body(), null);
    }

    /**
     * Percent-encodes each segment of a path, keeping the {@code /} separators, so method names
     * such as {@code orders/42} stay a path while spaces and {@code ?} cannot break the URL.
     *
     * @param path the raw path
     * @return the encoded path
     */
    public static String encodePath(String path) {
        StringBuilder out = new StringBuilder(path.length() + 8);
        for (byte b : path.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if (isPathChar(c)) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xf, 16)));
            }
        }
        return out.toString();
    }

    /** Encodes one path segment ({@code /} is encoded too). */
    public static String encodeSegment(String segment) {
        return encodePath(segment).replace("/", "%2F");
    }

    private static boolean isPathChar(int c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || "-._~!$&'()*+,;=:@/%".indexOf(c) >= 0;
    }

    private static String queryString(Map<String, List<String>> query) {
        if (query == null || query.isEmpty()) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("&", "?", "");
        query.forEach((name, values) -> {
            for (String value : values) {
                joiner.add(encodeQuery(name) + "=" + encodeQuery(value == null ? "" : value));
            }
        });
        return joiner.toString();
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static boolean containsIgnoreCase(Map<String, String> headers, String name) {
        return headers.keySet().stream().anyMatch(name::equalsIgnoreCase);
    }

    /** Headers the JDK client sets itself and refuses to accept from callers. */
    private static boolean isRestricted(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("host") || lower.equals("content-length") || lower.equals("connection")
                || lower.equals("expect") || lower.equals("upgrade");
    }

    private static String textOrNull(JsonNode json, String field) {
        JsonNode node = json.get(field);
        return node == null || node.isNull() ? null : node.asText();
    }
}
