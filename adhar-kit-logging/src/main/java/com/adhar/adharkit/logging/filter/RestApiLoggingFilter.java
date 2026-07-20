package com.adhar.adharkit.logging.filter;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.event.Level;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Servlet filter that logs every REST API exchange as a structured
 * {@link AppLogEventType#API} event.
 *
 * <p>Captured per request: HTTP method, path (and optionally query string), response status,
 * duration, client IP, user agent, payload sizes and — when enabled — masked headers and
 * truncated, masked request/response payloads. Requests slower than
 * {@code adhar.logging.rest-api.slow-request-threshold-ms} are flagged {@code slow} and logged at
 * WARN; 5xx responses and unhandled exceptions are logged at ERROR.</p>
 *
 * <p>Paths matching {@code adhar.logging.rest-api.exclude-paths} (Ant-style) are skipped. The
 * filter is registered after {@link MdcLoggingFilter} so events inherit full MDC context.</p>
 */
public class RestApiLoggingFilter extends OncePerRequestFilter {

    private final AdharLoggingProperties.RestApiProperties restApiProperties;
    private final AppLogEventPublisher publisher;
    private final LogDataMasker masker;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Creates the filter.
     *
     * @param properties logging properties (rest-api section)
     * @param publisher  event pipeline
     * @param masker     masker for headers and payloads
     */
    public RestApiLoggingFilter(AdharLoggingProperties properties, AppLogEventPublisher publisher,
                                LogDataMasker masker) {
        this.restApiProperties = properties.getRestApi();
        this.publisher = publisher;
        this.masker = masker;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String pattern : restApiProperties.getExcludePaths()) {
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest effectiveRequest = restApiProperties.isIncludeRequestPayload()
                ? new ContentCachingRequestWrapper(request, restApiProperties.getMaxPayloadLength())
                : request;
        ContentCachingResponseWrapper responseWrapper = restApiProperties.isIncludeResponsePayload()
                ? new ContentCachingResponseWrapper(response)
                : null;
        HttpServletResponse effectiveResponse = responseWrapper != null ? responseWrapper : response;

        long startNanos = System.nanoTime();
        Throwable failure = null;
        try {
            filterChain.doFilter(effectiveRequest, effectiveResponse);
        } catch (Throwable t) {
            failure = t;
            throw t;
        } finally {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            try {
                publishEvent(effectiveRequest, effectiveResponse, responseWrapper, durationMs, failure);
            } finally {
                if (responseWrapper != null) {
                    responseWrapper.copyBodyToResponse();
                }
            }
        }
    }

    private void publishEvent(HttpServletRequest request, HttpServletResponse response,
                              ContentCachingResponseWrapper responseWrapper,
                              long durationMs, Throwable failure) {
        int status = failure != null && response.getStatus() < 500
                ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                : response.getStatus();
        boolean slow = durationMs >= restApiProperties.getSlowRequestThresholdMs();

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("method", request.getMethod());
        metadata.put("path", request.getRequestURI());
        if (restApiProperties.isIncludeQueryString() && StringUtils.hasText(request.getQueryString())) {
            metadata.put("query", request.getQueryString());
        }
        metadata.put("status", status);
        metadata.put("slow", slow);
        String clientIp = request.getRemoteAddr();
        if (StringUtils.hasText(clientIp)) {
            metadata.put("clientIp", clientIp);
        }
        String userAgent = request.getHeader("User-Agent");
        if (StringUtils.hasText(userAgent)) {
            metadata.put("userAgent", userAgent);
        }
        if (restApiProperties.isIncludeHeaders()) {
            metadata.put("headers", maskedHeaders(request));
        }
        if (request instanceof ContentCachingRequestWrapper requestWrapper) {
            metadata.put("requestPayload", payload(requestWrapper.getContentAsByteArray(),
                    requestWrapper.getCharacterEncoding()));
        }
        if (responseWrapper != null) {
            metadata.put("responsePayload", payload(responseWrapper.getContentAsByteArray(),
                    responseWrapper.getCharacterEncoding()));
            metadata.put("responseSize", responseWrapper.getContentSize());
        }

        publisher.publish(AppLogEvent.builder()
                .type(AppLogEventType.API)
                .category("http")
                .name(request.getMethod() + " " + request.getRequestURI())
                .message(failure != null ? "Request failed" : (slow ? "Slow request" : "Request completed"))
                .outcome(outcomeFor(status, failure))
                .severity(severityFor(status, failure, slow))
                .durationMs(durationMs)
                .error(failure)
                .metadata(metadata)
                .build());
    }

    private Map<String, String> maskedHeaders(HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getHeader(name);
            boolean sensitive = restApiProperties.getMaskedHeaders().contains(name.toLowerCase(Locale.ROOT))
                    || masker.isSensitiveKey(name);
            headers.put(name, sensitive ? masker.applyStrategy(value) : value);
        }
        return headers;
    }

    private String payload(byte[] content, String encoding) {
        if (content == null || content.length == 0) {
            return "";
        }
        Charset charset;
        try {
            charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        } catch (Exception e) {
            charset = StandardCharsets.UTF_8;
        }
        String text = new String(content, charset);
        if (text.length() > restApiProperties.getMaxPayloadLength()) {
            text = text.substring(0, restApiProperties.getMaxPayloadLength()) + "...[truncated]";
        }
        return masker.maskText(text);
    }

    private static AppLogEventOutcome outcomeFor(int status, Throwable failure) {
        if (failure != null || status >= 500) {
            return AppLogEventOutcome.FAILURE;
        }
        if (status == 401 || status == 403) {
            return AppLogEventOutcome.DENIED;
        }
        if (status >= 400) {
            return AppLogEventOutcome.FAILURE;
        }
        return AppLogEventOutcome.SUCCESS;
    }

    private static Level severityFor(int status, Throwable failure, boolean slow) {
        if (failure != null || status >= 500) {
            return Level.ERROR;
        }
        if (status >= 400 || slow) {
            return Level.WARN;
        }
        return Level.INFO;
    }
}
