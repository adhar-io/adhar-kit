package com.adhar.kit.metrics.web;

import com.adhar.kit.metrics.slo.SloRecorder;
import com.adhar.kit.metrics.util.TagCardinalityLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Servlet filter that records a per-request {@link Timer} with the real response status.
 * <p>
 * Each request is recorded under {@value #METRIC_NAME} with the tags:
 * </p>
 * <ul>
 *   <li>{@code method} - the HTTP method</li>
 *   <li>{@code status} - the actual response status code (500 when the chain throws)</li>
 *   <li>{@code outcome} - the status class: {@code 1xx}..{@code 5xx}</li>
 *   <li>{@code uri} - a cardinality-safe uri: the best-matching handler pattern when
 *       available, otherwise a normalized path where numeric and UUID segments are
 *       replaced with {@code {id}}; additionally bounded by a {@link TagCardinalityLimiter}</li>
 * </ul>
 * <p>
 * When an {@link SloRecorder} is provided, every request outcome (success = status &lt; 500)
 * is also fed into the SLO error-budget tracking.
 * </p>
 */
@Slf4j
public class HttpMetricsFilter extends OncePerRequestFilter {

    /**
     * Name of the per-request timer metric.
     */
    public static final String METRIC_NAME = "adhar.http.server.requests";

    /**
     * Request attribute holding the best-matching handler pattern
     * (value of {@code org.springframework.web.servlet.HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE}).
     * Referenced by value so the filter does not require spring-webmvc on the classpath.
     */
    public static final String BEST_MATCHING_PATTERN_ATTRIBUTE =
            "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";

    /**
     * Tag key used for the request uri.
     */
    public static final String URI_TAG = "uri";

    private static final Pattern NUMERIC_SEGMENT = Pattern.compile("\\d+");
    private static final Pattern UUID_SEGMENT = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final int MAX_PATH_SEGMENTS = 10;

    private final MeterRegistry registry;
    private final TagCardinalityLimiter cardinalityLimiter;
    private final SloRecorder sloRecorder;

    /**
     * Creates the filter without SLO tracking.
     *
     * @param registry the meter registry
     * @param cardinalityLimiter limiter applied to the uri tag
     */
    public HttpMetricsFilter(MeterRegistry registry, TagCardinalityLimiter cardinalityLimiter) {
        this(registry, cardinalityLimiter, null);
    }

    /**
     * Creates the filter.
     *
     * @param registry the meter registry
     * @param cardinalityLimiter limiter applied to the uri tag
     * @param sloRecorder optional SLO recorder fed with request outcomes (may be null)
     */
    public HttpMetricsFilter(MeterRegistry registry, TagCardinalityLimiter cardinalityLimiter,
                             SloRecorder sloRecorder) {
        this.registry = registry;
        this.cardinalityLimiter = cardinalityLimiter;
        this.sloRecorder = sloRecorder;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startNanos = System.nanoTime();
        int status = 500;
        try {
            filterChain.doFilter(request, response);
            status = response.getStatus();
        } finally {
            recordRequest(request, status, System.nanoTime() - startNanos);
        }
    }

    private void recordRequest(HttpServletRequest request, int status, long durationNanos) {
        try {
            String uri = cardinalityLimiter.limit(URI_TAG, resolveUri(request));
            String method = request.getMethod() == null ? "UNKNOWN" : request.getMethod();

            Timer.builder(METRIC_NAME)
                    .description("HTTP server request timing with actual response status")
                    .tag("method", method)
                    .tag("status", String.valueOf(status))
                    .tag("outcome", outcomeOf(status))
                    .tag(URI_TAG, uri)
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);

            if (sloRecorder != null) {
                sloRecorder.recordHttp(uri, status < 500);
            }
        } catch (Exception e) {
            log.warn("Failed to record HTTP request metrics: {}", e.getMessage());
        }
    }

    /**
     * Resolves the cardinality-safe uri for a request: the best-matching handler
     * pattern when present, otherwise the normalized request path.
     *
     * @param request the current request
     * @return the uri tag value
     */
    static String resolveUri(HttpServletRequest request) {
        Object bestPattern = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (bestPattern != null) {
            String pattern = String.valueOf(bestPattern);
            if (!pattern.isEmpty()) {
                return pattern;
            }
        }
        return normalizePath(request.getRequestURI());
    }

    /**
     * Normalizes a raw request path into a bounded template: numeric and UUID
     * segments become {@code {id}} and the number of segments is capped.
     *
     * @param path the raw request path
     * @return the normalized path (never null)
     */
    static String normalizePath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "/";
        }

        String[] segments = path.split("/");
        StringBuilder normalized = new StringBuilder();
        int emitted = 0;
        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            if (emitted >= MAX_PATH_SEGMENTS) {
                normalized.append("/**");
                break;
            }
            normalized.append('/');
            if (NUMERIC_SEGMENT.matcher(segment).matches() || UUID_SEGMENT.matcher(segment).matches()) {
                normalized.append("{id}");
            } else {
                normalized.append(segment);
            }
            emitted++;
        }
        return normalized.length() == 0 ? "/" : normalized.toString();
    }

    private static String outcomeOf(int status) {
        int statusClass = status / 100;
        if (statusClass < 1 || statusClass > 5) {
            return "unknown";
        }
        return statusClass + "xx";
    }
}
