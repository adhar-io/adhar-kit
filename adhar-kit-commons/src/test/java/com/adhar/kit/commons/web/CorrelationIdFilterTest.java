package com.adhar.kit.commons.web;

import com.adhar.kit.commons.constant.CommonConstants;
import com.adhar.kit.commons.context.CorrelationContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @AfterEach
    void cleanup() {
        CorrelationContext.clear();
        MDC.clear();
    }

    @Test
    void providedHeaders_shouldBePropagatedAndEchoed() throws Exception {
        request.addHeader(CommonConstants.HEADER_CORRELATION_ID, "corr-1");
        request.addHeader(CommonConstants.HEADER_REQUEST_ID, "req-1");
        String[] seen = new String[4];

        filter.doFilter(request, response, (req, res) -> {
            seen[0] = CorrelationContext.getCorrelationId();
            seen[1] = CorrelationContext.getRequestId();
            seen[2] = MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID);
            seen[3] = MDC.get(CorrelationIdFilter.MDC_REQUEST_ID);
        });

        assertThat(seen).containsExactly("corr-1", "req-1", "corr-1", "req-1");
        assertThat(response.getHeader(CommonConstants.HEADER_CORRELATION_ID)).isEqualTo("corr-1");
        assertThat(response.getHeader(CommonConstants.HEADER_REQUEST_ID)).isEqualTo("req-1");
    }

    @Test
    void missingHeaders_shouldGenerateIds() throws Exception {
        String[] seen = new String[2];
        filter.doFilter(request, response, (req, res) -> {
            seen[0] = CorrelationContext.getCorrelationId();
            seen[1] = CorrelationContext.getRequestId();
        });

        assertThat(seen[0]).isNotBlank();
        assertThat(seen[1]).isNotBlank();
        assertThat(response.getHeader(CommonConstants.HEADER_CORRELATION_ID)).isEqualTo(seen[0]);
        assertThat(response.getHeader(CommonConstants.HEADER_REQUEST_ID)).isEqualTo(seen[1]);
    }

    @Test
    void blankHeader_shouldGenerateId() throws Exception {
        request.addHeader(CommonConstants.HEADER_CORRELATION_ID, "   ");
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getHeader(CommonConstants.HEADER_CORRELATION_ID)).isNotBlank();
        assertThat(response.getHeader(CommonConstants.HEADER_CORRELATION_ID).trim()).isNotEmpty();
    }

    @Test
    void contextAndMdc_shouldBeClearedAfterRequest() throws Exception {
        request.addHeader(CommonConstants.HEADER_CORRELATION_ID, "corr-1");
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(CorrelationContext.getCorrelationId()).isNull();
        assertThat(CorrelationContext.getRequestId()).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID)).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_REQUEST_ID)).isNull();
    }

    @Test
    void contextAndMdc_shouldBeClearedWhenChainThrows() {
        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new ServletException("boom");
        })).isInstanceOf(ServletException.class);

        assertThat(CorrelationContext.getCorrelationId()).isNull();
        assertThat(MDC.get(CorrelationIdFilter.MDC_CORRELATION_ID)).isNull();
    }

    @Test
    void nonHttpRequest_shouldPassThroughUntouched() throws Exception {
        ServletRequest plainRequest = mock(ServletRequest.class);
        ServletResponse plainResponse = mock(ServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(plainRequest, plainResponse, chain);

        verify(chain).doFilter(plainRequest, plainResponse);
        assertThat(CorrelationContext.getCorrelationId()).isNull();
    }

    @Test
    void order_shouldRunVeryEarly() {
        assertThat(filter.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 10);
    }
}
