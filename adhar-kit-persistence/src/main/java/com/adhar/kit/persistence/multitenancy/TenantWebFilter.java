package com.adhar.kit.persistence.multitenancy;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Servlet {@link Filter} that populates {@link TenantContext} for the duration of an incoming
 * HTTP request from the {@value #TENANT_HEADER_NAME} header, and always clears it afterwards.
 *
 * <p>The {@code jakarta.servlet-api} dependency this class compiles against is marked
 * {@code optional}/{@code provided} in the module POM: consumers that never register a servlet
 * container (pure batch/messaging applications) are not forced to bring the Servlet API onto
 * their classpath. The corresponding bean in {@code PersistenceAutoConfiguration} is guarded with
 * {@code @ConditionalOnClass(name = "jakarta.servlet.Filter")} so the auto-configuration itself
 * never fails to load in a non-web application.</p>
 *
 * @author Adhar Platform Team
 * @since 1.3.0
 */
public class TenantWebFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(TenantWebFilter.class);

    /** HTTP header consulted to resolve the current request's tenant identifier. */
    public static final String TENANT_HEADER_NAME = "X-Tenant-ID";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String tenantId = resolveTenantId(request);
        try {
            if (tenantId != null && !tenantId.isBlank()) {
                log.debug("Setting tenant context to '{}' for incoming request", tenantId);
                TenantContext.setTenant(tenantId);
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String resolveTenantId(ServletRequest request) {
        if (request instanceof HttpServletRequest httpRequest) {
            return httpRequest.getHeader(TENANT_HEADER_NAME);
        }
        return null;
    }
}
