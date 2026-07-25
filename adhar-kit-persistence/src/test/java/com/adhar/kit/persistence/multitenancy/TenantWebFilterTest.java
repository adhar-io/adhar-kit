package com.adhar.kit.persistence.multitenancy;

import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("TenantWebFilter Tests")
class TenantWebFilterTest {

    private final TenantWebFilter filter = new TenantWebFilter();

    @BeforeEach
    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("populates TenantContext from the X-Tenant-ID header for the duration of the request")
    void populatesTenantContextFromHeader() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantWebFilter.TENANT_HEADER_NAME, "acme_corp");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> tenantDuringRequest = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                tenantDuringRequest.set(TenantContext.getTenant());
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals("acme_corp", tenantDuringRequest.get());
        assertFalse(TenantContext.hasTenant(), "tenant context must be cleared after the request");
    }

    @Test
    @DisplayName("leaves TenantContext unset when no header is present")
    void leavesTenantContextUnsetWithoutHeader() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<String> tenantDuringRequest = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                tenantDuringRequest.set(TenantContext.hasTenant() ? TenantContext.getTenant() : null);
            }
        };

        filter.doFilter(request, response, chain);

        assertNull(tenantDuringRequest.get());
    }

    @Test
    @DisplayName("ignores a blank header value")
    void ignoresBlankHeader() throws IOException, ServletException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantWebFilter.TENANT_HEADER_NAME, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        AtomicReference<Boolean> hadTenant = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                hadTenant.set(TenantContext.hasTenant());
            }
        };

        filter.doFilter(request, response, chain);

        assertEquals(Boolean.FALSE, hadTenant.get());
    }

    @Test
    @DisplayName("clears TenantContext even when the downstream chain throws")
    void clearsTenantContextEvenOnDownstreamFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(TenantWebFilter.TENANT_HEADER_NAME, "acme_corp");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(ServletRequest req, ServletResponse res) {
                throw new IllegalStateException("downstream boom");
            }
        };

        assertTrue(assertThrowsIllegalState(() -> filter.doFilter(request, response, chain)));
        assertFalse(TenantContext.hasTenant());
    }

    private boolean assertThrowsIllegalState(ThrowingRunnable runnable) {
        try {
            runnable.run();
            return false;
        } catch (IllegalStateException expected) {
            return true;
        } catch (Exception unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
