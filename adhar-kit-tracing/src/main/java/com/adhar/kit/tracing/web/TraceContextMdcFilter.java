package com.adhar.kit.tracing.web;

import com.adhar.kit.tracing.util.AdharTracing;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that injects the current request's {@code traceId}/{@code spanId} (as
 * reported by {@link AdharTracing#getCurrentTraceId()} / {@link AdharTracing#getCurrentSpanId()})
 * into the SLF4J {@link MDC} for the duration of the request, so log statements emitted while
 * handling the request can be correlated with the corresponding trace/span (e.g. via a logging
 * pattern that includes {@code %X{traceId}}/{@code %X{spanId}}).
 * <p>
 * Requests whose path matches one of the configured {@code skipPatterns}
 * (see {@code AdharTracingProperties.WebTracingProperties#getSkipPatterns()}) are not
 * filtered (mirroring {@code OncePerRequestFilter#shouldNotFilter}), and the MDC entries are
 * always removed in a {@code finally} block so they never leak into unrelated log statements
 * on a pooled thread.
 * </p>
 */
public class TraceContextMdcFilter extends OncePerRequestFilter {

    /** MDC key under which the current trace ID is published. */
    public static final String TRACE_ID_MDC_KEY = "traceId";

    /** MDC key under which the current span ID is published. */
    public static final String SPAN_ID_MDC_KEY = "spanId";

    private final AdharTracing adharTracing;
    private final String[] skipPatterns;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TraceContextMdcFilter(AdharTracing adharTracing, String[] skipPatterns) {
        this.adharTracing = adharTracing;
        this.skipPatterns = skipPatterns != null ? skipPatterns.clone() : new String[0];
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!StringUtils.hasText(path)) {
            return false;
        }
        for (String pattern : skipPatterns) {
            if (StringUtils.hasText(pattern) && pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = adharTracing.getCurrentTraceId();
        String spanId = adharTracing.getCurrentSpanId();
        boolean traceIdSet = false;
        boolean spanIdSet = false;

        try {
            if (StringUtils.hasText(traceId)) {
                MDC.put(TRACE_ID_MDC_KEY, traceId);
                traceIdSet = true;
            }
            if (StringUtils.hasText(spanId)) {
                MDC.put(SPAN_ID_MDC_KEY, spanId);
                spanIdSet = true;
            }
            filterChain.doFilter(request, response);
        } finally {
            if (traceIdSet) {
                MDC.remove(TRACE_ID_MDC_KEY);
            }
            if (spanIdSet) {
                MDC.remove(SPAN_ID_MDC_KEY);
            }
        }
    }
}
