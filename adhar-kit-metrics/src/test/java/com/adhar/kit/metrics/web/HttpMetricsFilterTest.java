package com.adhar.kit.metrics.web;

import com.adhar.kit.metrics.properties.AdharMetricsProperties;
import com.adhar.kit.metrics.slo.SloRecorder;
import com.adhar.kit.metrics.util.TagCardinalityLimiter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link HttpMetricsFilter} using mock servlet objects.
 */
class HttpMetricsFilterTest {

    private SimpleMeterRegistry registry;
    private HttpMetricsFilter filter;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        filter = new HttpMetricsFilter(registry, new TagCardinalityLimiter(100));
    }

    private static MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        return request;
    }

    private static FilterChain chainWithStatus(int status) {
        return (req, res) -> ((HttpServletResponse) res).setStatus(status);
    }

    @Test
    void successfulRequest_recordsTimerWithRealStatusAndOutcome() throws Exception {
        filter.doFilter(request("GET", "/api/orders"), new MockHttpServletResponse(), chainWithStatus(200));

        Timer timer = registry.find(HttpMetricsFilter.METRIC_NAME)
                .tag("method", "GET")
                .tag("status", "200")
                .tag("outcome", "2xx")
                .tag("uri", "/api/orders")
                .timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1L);
    }

    @Test
    void clientError_recordsActual4xxStatus() throws Exception {
        filter.doFilter(request("GET", "/api/missing"), new MockHttpServletResponse(), chainWithStatus(404));

        assertThat(registry.find(HttpMetricsFilter.METRIC_NAME)
                .tag("status", "404")
                .tag("outcome", "4xx")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void serverError_recordsActual5xxStatus() throws Exception {
        filter.doFilter(request("POST", "/api/orders"), new MockHttpServletResponse(), chainWithStatus(503));

        assertThat(registry.find(HttpMetricsFilter.METRIC_NAME)
                .tag("method", "POST")
                .tag("status", "503")
                .tag("outcome", "5xx")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void chainException_recordsStatus500AndRethrows() {
        FilterChain failingChain = (req, res) -> {
            throw new ServletException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request("GET", "/api/fail"),
                new MockHttpServletResponse(), failingChain))
                .isInstanceOf(ServletException.class);

        assertThat(registry.find(HttpMetricsFilter.METRIC_NAME)
                .tag("status", "500")
                .tag("outcome", "5xx")
                .tag("uri", "/api/fail")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void bestMatchingPatternAttribute_takesPrecedenceOverRawUri() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/orders/12345");
        request.setAttribute(HttpMetricsFilter.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/orders/{orderId}");

        filter.doFilter(request, new MockHttpServletResponse(), chainWithStatus(200));

        assertThat(registry.find(HttpMetricsFilter.METRIC_NAME)
                .tag("uri", "/api/orders/{orderId}")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void numericAndUuidSegments_areTemplatedWhenNoPatternAttribute() throws Exception {
        filter.doFilter(request("GET", "/api/orders/12345/items/550e8400-e29b-41d4-a716-446655440000"),
                new MockHttpServletResponse(), chainWithStatus(200));

        assertThat(registry.find(HttpMetricsFilter.METRIC_NAME)
                .tag("uri", "/api/orders/{id}/items/{id}")
                .timer().count()).isEqualTo(1L);
    }

    @Test
    void normalizePath_handlesEdgeCases() {
        assertThat(HttpMetricsFilter.normalizePath(null)).isEqualTo("/");
        assertThat(HttpMetricsFilter.normalizePath("")).isEqualTo("/");
        assertThat(HttpMetricsFilter.normalizePath("/")).isEqualTo("/");
        assertThat(HttpMetricsFilter.normalizePath("//")).isEqualTo("/");
        assertThat(HttpMetricsFilter.normalizePath("/users/42")).isEqualTo("/users/{id}");
        assertThat(HttpMetricsFilter.normalizePath("/a/b/c/d/e/f/g/h/i/j/k/l"))
                .isEqualTo("/a/b/c/d/e/f/g/h/i/j/**");
    }

    @Test
    void uriTag_isBoundedByCardinalityLimiter() throws Exception {
        HttpMetricsFilter limitedFilter = new HttpMetricsFilter(registry, new TagCardinalityLimiter(2));

        limitedFilter.doFilter(request("GET", "/a"), new MockHttpServletResponse(), chainWithStatus(200));
        limitedFilter.doFilter(request("GET", "/b"), new MockHttpServletResponse(), chainWithStatus(200));
        limitedFilter.doFilter(request("GET", "/c"), new MockHttpServletResponse(), chainWithStatus(200));
        limitedFilter.doFilter(request("GET", "/d"), new MockHttpServletResponse(), chainWithStatus(200));

        assertThat(registry.find(HttpMetricsFilter.METRIC_NAME)
                .tag("uri", TagCardinalityLimiter.OVERFLOW_VALUE)
                .timer().count()).isEqualTo(2L);
        assertThat(registry.find(HttpMetricsFilter.METRIC_NAME).tag("uri", "/a").timer()).isNotNull();
        assertThat(registry.find(HttpMetricsFilter.METRIC_NAME).tag("uri", "/c").timer()).isNull();
    }

    @Test
    void sloRecorder_receivesRequestOutcomes() throws Exception {
        AdharMetricsProperties.SloProperties sloProps = new AdharMetricsProperties.SloProperties();
        sloProps.setTargets(Map.of(SloRecorder.GLOBAL_TARGET, 0.99));
        SloRecorder sloRecorder = new SloRecorder(registry, sloProps);
        HttpMetricsFilter sloFilter = new HttpMetricsFilter(registry, new TagCardinalityLimiter(100), sloRecorder);

        // 199 successes and one 5xx: observed error rate 0.005 = half the 0.01 allowance
        for (int i = 0; i < 199; i++) {
            sloFilter.doFilter(request("GET", "/api/pay"), new MockHttpServletResponse(), chainWithStatus(200));
        }
        sloFilter.doFilter(request("GET", "/api/pay"), new MockHttpServletResponse(), chainWithStatus(500));

        assertThat(sloRecorder.burnRate(SloRecorder.GLOBAL_TARGET)).isCloseTo(0.5, within(1e-9));
        assertThat(sloRecorder.errorBudgetRemaining(SloRecorder.GLOBAL_TARGET)).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void clientErrors_doNotBurnSloBudget() throws Exception {
        AdharMetricsProperties.SloProperties sloProps = new AdharMetricsProperties.SloProperties();
        sloProps.setTargets(Map.of(SloRecorder.GLOBAL_TARGET, 0.99));
        SloRecorder sloRecorder = new SloRecorder(registry, sloProps);
        HttpMetricsFilter sloFilter = new HttpMetricsFilter(registry, new TagCardinalityLimiter(100), sloRecorder);

        sloFilter.doFilter(request("GET", "/api/pay"), new MockHttpServletResponse(), chainWithStatus(404));

        assertThat(sloRecorder.burnRate(SloRecorder.GLOBAL_TARGET)).isEqualTo(0.0);
        assertThat(sloRecorder.errorBudgetRemaining(SloRecorder.GLOBAL_TARGET)).isEqualTo(1.0);
    }
}
