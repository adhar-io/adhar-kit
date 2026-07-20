package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.context.TenantContext;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.core.Ordered;

import java.io.IOException;

/**
 * Servlet filter that establishes the tenant context for every request.
 *
 * <p>Reads the {@code X-Tenant-ID} header and, when present, populates
 * {@link TenantContext} and the SLF4J MDC ({@code tenantId}). Context and MDC are
 * always cleared in a {@code finally} block. Requests without the header pass through
 * with no tenant bound.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public class TenantContextFilter implements Filter, Ordered {

    /** MDC key holding the tenant id. */
    public static final String MDC_TENANT_ID = "tenantId";

    /** Runs right after {@link CorrelationIdFilter}. */
    public static final int ORDER = CorrelationIdFilter.ORDER + 10;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (!(request instanceof HttpServletRequest httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String tenantId = httpRequest.getHeader(CommonConstants.HEADER_TENANT_ID);
        if (tenantId != null && !tenantId.isBlank()) {
            TenantContext.setTenantId(tenantId);
            MDC.put(MDC_TENANT_ID, tenantId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            MDC.remove(MDC_TENANT_ID);
        }
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
