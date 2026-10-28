package com.adhar.kit.dapr.observation;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Trace context and API version of the inbound servlet request being handled on this thread.
 * Used when no tracer is configured: the Dapr call then continues the caller's trace directly.
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class RequestHeaderTraceContext implements DaprTraceContext {

    /** The API version header of the adhar-kit versioning contract. */
    public static final String API_VERSION = "API-Version";

    @Override
    public Map<String, String> current() {
        return headers(DaprTraceContext.TRACEPARENT, DaprTraceContext.TRACESTATE);
    }

    /**
     * Reads headers of the current inbound request.
     *
     * @param names header names
     * @return the headers present, keyed by the given names
     */
    public static Map<String, String> headers(String... names) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return Map.of();
        }
        HttpServletRequest request = servlet.getRequest();
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : names) {
            String value = request.getHeader(name);
            if (value != null && !value.isBlank()) {
                headers.put(name, value);
            }
        }
        return headers;
    }
}
