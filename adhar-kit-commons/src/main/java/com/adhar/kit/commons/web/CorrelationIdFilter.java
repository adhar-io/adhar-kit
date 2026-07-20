package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.context.CorrelationContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that establishes the correlation context for every request.
 *
 * <p>Reads the {@code X-Correlation-ID} and {@code X-Request-ID} headers (generating
 * UUIDs when absent), populates {@link CorrelationContext} and the SLF4J MDC
 * ({@code correlationId} / {@code requestId}), and echoes both ids back on the
 * response. Context and MDC are always cleared in a {@code finally} block.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class CorrelationIdFilter implements Filter, Ordered {

    /** MDC key holding the correlation id. */
    public static final String MDC_CORRELATION_ID = "correlationId";

    /** MDC key holding the request id. */
    public static final String MDC_REQUEST_ID = "requestId";

    /** Runs very early so every downstream component sees the correlation context. */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)
                || !(response instanceof HttpServletResponse httpResponse)) {
            chain.doFilter(request, response);
            return;
        }

        String correlationId = headerOrGenerate(httpRequest, CommonConstants.HEADER_CORRELATION_ID);
        String requestId = headerOrGenerate(httpRequest, CommonConstants.HEADER_REQUEST_ID);
        CorrelationContext.setCorrelationId(correlationId);
        CorrelationContext.setRequestId(requestId);
        MDC.put(MDC_CORRELATION_ID, correlationId);
        MDC.put(MDC_REQUEST_ID, requestId);
        httpResponse.setHeader(CommonConstants.HEADER_CORRELATION_ID, correlationId);
        httpResponse.setHeader(CommonConstants.HEADER_REQUEST_ID, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            CorrelationContext.clear();
            MDC.remove(MDC_CORRELATION_ID);
            MDC.remove(MDC_REQUEST_ID);
        }
    }

    private static String headerOrGenerate(HttpServletRequest request, String headerName) {
        String value = request.getHeader(headerName);
        return (value == null || value.isBlank()) ? UUID.randomUUID().toString() : value;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
