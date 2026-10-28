package com.adhar.kit.dapr.invoke;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the invoked app answered (a 2xx; anything else surfaces as a
 * {@link com.adhar.kit.dapr.DaprError} carrying the status and body).
 *
 * @param status  the HTTP status
 * @param headers the response headers (names lower-cased)
 * @param body    the response body (may be empty)
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public record InvokeResponse(int status, Map<String, List<String>> headers, byte[] body) {

    public InvokeResponse {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
        body = body == null ? new byte[0] : body;
    }

    /** The first value of a header (case-insensitive), or {@code null}. */
    public String header(String name) {
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        return values == null || values.isEmpty() ? null : values.getFirst();
    }

    /** The body as UTF-8 text. */
    public String bodyAsString() {
        return new String(body, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof InvokeResponse other && status == other.status
                && headers.equals(other.headers) && Arrays.equals(body, other.body);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * status + headers.hashCode()) + Arrays.hashCode(body);
    }

    @Override
    public String toString() {
        return "InvokeResponse[status=" + status + ", body=" + body.length + " bytes]";
    }
}
