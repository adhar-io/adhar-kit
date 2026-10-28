package com.adhar.kit.commons.web;

import com.adhar.kit.commons.context.CorrelationContext;
import com.adhar.kit.commons.model.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RFC 9457 problem details as the platform's services return them: the standard
 * members ({@code type}, {@code title}, {@code status}, {@code detail},
 * {@code instance}) plus {@code code} (the kit's error code), {@code requestId}
 * (correlates with the logs), and {@code fieldErrors}/{@code errors} for validation
 * failures.
 *
 * <p>Inside Spring MVC return a {@link ProblemDetail} (or throw an
 * {@link org.springframework.web.ErrorResponseException}); code that runs before
 * the DispatcherServlet, such as a filter, writes one with
 * {@link #write(HttpServletResponse, ProblemDetail)}.</p>
 *
 * @author Adhar Platform Team
 * @since 0.2.0
 */
public final class ProblemDetails {

    /** {@code application/problem+json}. */
    public static final String MEDIA_TYPE = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

    private ProblemDetails() {
    }

    /**
     * A problem for the given status, with the request path as {@code instance} and the
     * request id of the current request.
     *
     * @param status  the HTTP status
     * @param detail  what went wrong, for the client
     * @param request the request, or null
     * @return the problem
     */
    public static ProblemDetail of(HttpStatusCode status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (request != null) {
            instance(problem, request.getRequestURI());
        }
        String requestId = CorrelationContext.getRequestId();
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        return problem;
    }

    /**
     * The kit's {@link ErrorResponse} as a problem. A 5xx keeps its {@code details} out
     * of the problem: for unexpected failures they are an exception message, which is
     * for the logs, not the client.
     *
     * @param status the HTTP status of the response
     * @param error  the error body
     * @return the problem
     */
    public static ProblemDetail of(int status, ErrorResponse error) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setDetail(error.getMessage());
        instance(problem, error.getPath());
        if (error.getCode() != null) {
            problem.setProperty("code", error.getCode());
        }
        if (status < 500 && error.getDetails() != null) {
            problem.setProperty("details", error.getDetails());
        }
        if (error.getFieldErrors() != null && !error.getFieldErrors().isEmpty()) {
            problem.setProperty("fieldErrors", error.getFieldErrors());
        }
        if (error.getValidationErrors() != null && !error.getValidationErrors().isEmpty()) {
            problem.setProperty("errors", error.getValidationErrors());
        }
        if (error.getRequestId() != null) {
            problem.setProperty("requestId", error.getRequestId());
        }
        if (error.getTimestamp() != null) {
            problem.setProperty("timestamp",
                    error.getTimestamp().atZone(ZoneId.systemDefault()).toOffsetDateTime().toString());
        }
        return problem;
    }

    /**
     * The problem as an ordered map - the members RFC 9457 defines first, then the
     * extension members at the top level - which every JSON mapper writes the same way.
     *
     * @param problem the problem
     * @return the members
     */
    public static Map<String, Object> toMap(ProblemDetail problem) {
        Map<String, Object> members = new LinkedHashMap<>();
        // Absent means about:blank (RFC 9457 3.1.1), which is how Spring writes it too.
        if (problem.getType() != null) {
            members.put("type", problem.getType().toString());
        }
        if (problem.getTitle() != null) {
            members.put("title", problem.getTitle());
        }
        members.put("status", problem.getStatus());
        if (problem.getDetail() != null) {
            members.put("detail", problem.getDetail());
        }
        if (problem.getInstance() != null) {
            members.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            problem.getProperties().forEach(members::putIfAbsent);
        }
        return members;
    }

    /**
     * Writes the problem as the whole response, for code that runs outside Spring MVC.
     *
     * @param response the response, not yet committed
     * @param problem  the problem
     * @throws IOException if the body cannot be written
     */
    public static void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        byte[] body = toJson(toMap(problem)).getBytes(StandardCharsets.UTF_8);
        response.setStatus(problem.getStatus());
        response.setContentType(MEDIA_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    private static void instance(ProblemDetail problem, String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        try {
            problem.setInstance(URI.create(path));
        } catch (IllegalArgumentException notAUri) {
            // A path the container accepted but java.net.URI does not: leave instance out.
        }
    }

    /**
     * A JSON encoding of maps, collections, strings, numbers and booleans - all a problem
     * holds - so writing one never depends on which Jackson the application runs.
     */
    static String toJson(Object value) {
        StringBuilder json = new StringBuilder();
        append(json, value);
        return json.toString();
    }

    private static void append(StringBuilder json, Object value) {
        if (value == null) {
            json.append("null");
        } else if (value instanceof Boolean || value instanceof Number) {
            json.append(value);
        } else if (value instanceof Map<?, ?> map) {
            json.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                appendString(json, String.valueOf(entry.getKey()));
                json.append(':');
                append(json, entry.getValue());
            }
            json.append('}');
        } else if (value instanceof Collection<?> collection) {
            json.append('[');
            boolean first = true;
            for (Object element : collection) {
                if (!first) {
                    json.append(',');
                }
                first = false;
                append(json, element);
            }
            json.append(']');
        } else {
            appendString(json, value.toString());
        }
    }

    private static void appendString(StringBuilder json, String text) {
        json.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (c < 0x20) {
                        json.append(String.format("\\u%04x", (int) c));
                    } else {
                        json.append(c);
                    }
                }
            }
        }
        json.append('"');
    }
}
