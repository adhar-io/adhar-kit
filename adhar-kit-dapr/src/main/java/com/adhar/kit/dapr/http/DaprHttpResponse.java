package com.adhar.kit.dapr.http;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A raw response from the sidecar's HTTP API.
 *
 * @param status  the status code
 * @param headers the headers (names lower-cased)
 * @param body    the body, never {@code null}
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record DaprHttpResponse(int status, Map<String, List<String>> headers, byte[] body) {

    public DaprHttpResponse {
        headers = headers == null ? Map.of() : headers;
        body = body == null ? new byte[0] : body;
    }

    public boolean isSuccessful() {
        return status >= 200 && status < 300;
    }

    public String header(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }
}
