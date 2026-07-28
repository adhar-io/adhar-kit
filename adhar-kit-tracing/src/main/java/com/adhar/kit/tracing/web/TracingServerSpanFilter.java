package com.adhar.kit.tracing.web;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Servlet filter that creates an OpenTelemetry {@code SERVER} span for every incoming HTTP
 * request.
 * <p>
 * The span is created via the SDK-backed {@link io.opentelemetry.api.trace.Tracer} obtained
 * from the shared {@link OpenTelemetry} instance, so it flows through the same sampler and
 * span processors (tail sampling, RED metrics, exporters) as the rest of the module. Because
 * the span is made {@linkplain Span#makeCurrent() current} for the duration of the request,
 * the Micrometer {@code Tracer} (and therefore {@link com.adhar.kit.tracing.util.AdharTracing}
 * and {@link TraceContextMdcFilter}) observe it as the active span.
 * </p>
 * <p>
 * Incoming W3C {@code traceparent}/{@code tracestate} (and any other configured propagation
 * format) is extracted using the {@link OpenTelemetry#getPropagators() configured propagators}
 * so the server span joins the remote trace as a child of the caller's span. The span is
 * tagged with {@code http.method}, {@code http.route} and {@code http.status_code}, and any
 * exception thrown downstream (or a {@code >= 500} response status) marks the span with
 * {@link StatusCode#ERROR}.
 * </p>
 * <p>
 * Requests whose path matches one of the configured {@code skipPatterns} are not traced
 * (mirroring {@code OncePerRequestFilter#shouldNotFilter}).
 * </p>
 */
public class TracingServerSpanFilter extends OncePerRequestFilter {

    /** Instrumentation scope name, kept consistent with the module's Micrometer tracer. */
    static final String INSTRUMENTATION_SCOPE = "adhar-kit-tracing";

    private static final TextMapGetter<HttpServletRequest> HEADER_GETTER = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest request) {
            if (request.getHeaderNames() == null) {
                return Collections.emptyList();
            }
            return Collections.list(request.getHeaderNames());
        }

        @Override
        public String get(HttpServletRequest request, String key) {
            return request == null ? null : request.getHeader(key);
        }
    };

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final String[] skipPatterns;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TracingServerSpanFilter(OpenTelemetry openTelemetry, String[] skipPatterns) {
        this.openTelemetry = openTelemetry;
        this.tracer = openTelemetry.getTracer(INSTRUMENTATION_SCOPE);
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

        String method = request.getMethod() != null ? request.getMethod() : "HTTP";
        String route = routeOf(request);

        // Join any incoming remote trace (W3C traceparent, B3, jaeger, ... depending on the
        // configured propagators) so the SERVER span is a child of the caller's span.
        Context extracted = openTelemetry.getPropagators().getTextMapPropagator()
                .extract(Context.current(), request, HEADER_GETTER);

        Span span = tracer.spanBuilder(method + " " + route)
                .setSpanKind(SpanKind.SERVER)
                .setParent(extracted)
                .startSpan();

        span.setAttribute("http.method", method);
        span.setAttribute("http.route", route);

        try (Scope ignored = span.makeCurrent()) {
            filterChain.doFilter(request, response);
            int status = response.getStatus();
            span.setAttribute("http.status_code", status);
            if (status >= 500) {
                span.setStatus(StatusCode.ERROR);
            }
        } catch (ServletException | IOException | RuntimeException | Error e) {
            span.setAttribute("http.status_code", response.getStatus());
            span.recordException(e);
            span.setStatus(StatusCode.ERROR, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Best-effort route for the request: prefer Spring MVC's best-matching pattern request
     * attribute (populated once the request has been dispatched), falling back to the raw URI
     * path. Reading it before {@code filterChain.doFilter} normally yields the URI; the span
     * name is set up-front from that value to keep the filter framework-agnostic.
     */
    private String routeOf(HttpServletRequest request) {
        Object bestMatch = request.getAttribute("org.springframework.web.servlet.HandlerMapping.bestMatchingPattern");
        if (bestMatch instanceof String pattern && StringUtils.hasText(pattern)) {
            return pattern;
        }
        String uri = request.getRequestURI();
        return StringUtils.hasText(uri) ? uri : "/";
    }
}
