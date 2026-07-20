package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TenantContextFilterTest {

    private final TenantContextFilter filter = new TenantContextFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        MDC.clear();
    }

    @Test
    void tenantHeader_shouldPopulateContextAndMdc() throws Exception {
        request.addHeader(CommonConstants.HEADER_TENANT_ID, "tenant-42");
        String[] seen = new String[2];

        filter.doFilter(request, response, (req, res) -> {
            seen[0] = TenantContext.getTenantId();
            seen[1] = MDC.get(TenantContextFilter.MDC_TENANT_ID);
        });

        assertThat(seen).containsExactly("tenant-42", "tenant-42");
    }

    @Test
    void missingHeader_shouldLeaveContextEmpty() throws Exception {
        String[] seen = new String[2];
        filter.doFilter(request, response, (req, res) -> {
            seen[0] = TenantContext.getTenantId();
            seen[1] = MDC.get(TenantContextFilter.MDC_TENANT_ID);
        });
        assertThat(seen[0]).isNull();
        assertThat(seen[1]).isNull();
    }

    @Test
    void blankHeader_shouldBeIgnored() throws Exception {
        request.addHeader(CommonConstants.HEADER_TENANT_ID, "  ");
        String[] seen = new String[1];
        filter.doFilter(request, response, (req, res) -> seen[0] = TenantContext.getTenantId());
        assertThat(seen[0]).isNull();
    }

    @Test
    void contextAndMdc_shouldBeClearedAfterRequest() throws Exception {
        request.addHeader(CommonConstants.HEADER_TENANT_ID, "tenant-42");
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(MDC.get(TenantContextFilter.MDC_TENANT_ID)).isNull();
    }

    @Test
    void contextAndMdc_shouldBeClearedWhenChainThrows() {
        request.addHeader(CommonConstants.HEADER_TENANT_ID, "tenant-42");
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(TenantContext.getTenantId()).isNull();
        assertThat(MDC.get(TenantContextFilter.MDC_TENANT_ID)).isNull();
    }

    @Test
    void nonHttpRequest_shouldPassThroughUntouched() throws Exception {
        ServletRequest plainRequest = mock(ServletRequest.class);
        ServletResponse plainResponse = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(plainRequest, plainResponse, chain);

        verify(chain).doFilter(plainRequest, plainResponse);
    }

    @Test
    void order_shouldRunAfterCorrelationFilter() {
        assertThat(filter.getOrder()).isGreaterThan(new CorrelationIdFilter().getOrder());
    }
}
