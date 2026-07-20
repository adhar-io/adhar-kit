package com.adhar.adharkit.logging.filter;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.event.RecordingAppLogEventSink;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RestApiLoggingFilter}.
 */
class RestApiLoggingFilterTest {

    private AdharLoggingProperties properties;
    private RecordingAppLogEventSink sink;
    private RestApiLoggingFilter filter;

    @BeforeEach
    void setUp() {
        properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        LogDataMasker masker = new LogDataMasker(properties.getMasking());
        filter = new RestApiLoggingFilter(properties,
                new AppLogEventPublisher(properties, masker, List.of(sink)), masker);
    }

    private MockHttpServletRequest request(String method, String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void successfulRequestPublishesApiEvent() throws Exception {
        MockHttpServletRequest request = request("GET", "/api/orders");
        request.setQueryString("page=1");
        request.addHeader("User-Agent", "junit");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.API);
        assertThat(event.getName()).isEqualTo("GET /api/orders");
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(event.getSeverity()).isEqualTo(Level.INFO);
        assertThat(event.getDurationMs()).isNotNull();
        assertThat(event.getMetadata())
                .containsEntry("method", "GET")
                .containsEntry("path", "/api/orders")
                .containsEntry("query", "page=1")
                .containsEntry("status", 200)
                .containsEntry("slow", false)
                .containsEntry("clientIp", "10.0.0.1")
                .containsEntry("userAgent", "junit");
    }

    @Test
    void clientErrorIsWarnFailureAndAuthIsDenied() throws Exception {
        MockHttpServletResponse notFound = new MockHttpServletResponse();
        notFound.setStatus(404);
        filter.doFilter(request("GET", "/api/missing"), notFound, new MockFilterChain());
        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(sink.last().getSeverity()).isEqualTo(Level.WARN);

        MockHttpServletResponse forbidden = new MockHttpServletResponse();
        forbidden.setStatus(403);
        filter.doFilter(request("GET", "/api/secret"), forbidden, new MockFilterChain());
        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.DENIED);
    }

    @Test
    void serverErrorIsErrorSeverity() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        filter.doFilter(request("POST", "/api/orders"), response, new MockFilterChain());

        assertThat(sink.last().getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(sink.last().getSeverity()).isEqualTo(Level.ERROR);
    }

    @Test
    void slowRequestIsFlaggedAndWarned() throws Exception {
        properties.getRestApi().setSlowRequestThresholdMs(0);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        filter.doFilter(request("GET", "/api/slow"), response, new MockFilterChain());

        assertThat(sink.last().getMetadata()).containsEntry("slow", true);
        assertThat(sink.last().getSeverity()).isEqualTo(Level.WARN);
    }

    @Test
    void unhandledExceptionIsLoggedAndRethrown() {
        MockHttpServletRequest request = request("GET", "/api/boom");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
            @Override
            protected void service(jakarta.servlet.http.HttpServletRequest req,
                                   jakarta.servlet.http.HttpServletResponse res) throws ServletException {
                throw new ServletException("boom");
            }
        });

        assertThatThrownBy(() -> filter.doFilter(request, response, chain))
                .isInstanceOf(ServletException.class);

        AppLogEvent event = sink.last();
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(event.getSeverity()).isEqualTo(Level.ERROR);
        assertThat(event.getErrorType()).isEqualTo(ServletException.class.getName());
        assertThat(event.getMetadata()).containsEntry("status", 500);
    }

    @Test
    void excludedPathsAreNotLogged() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request("GET", "/actuator/health"), response, new MockFilterChain());

        assertThat(sink.getEvents()).isEmpty();
    }

    @Test
    void headersAreCapturedAndMaskedWhenEnabled() throws Exception {
        properties.getRestApi().setIncludeHeaders(true);
        MockHttpServletRequest request = request("GET", "/api/orders");
        request.addHeader("Authorization", "Bearer secret-token");
        request.addHeader("Accept", "application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        @SuppressWarnings("unchecked")
        Map<String, String> headers = (Map<String, String>) sink.last().getMetadata().get("headers");
        assertThat(headers.get("Authorization")).isEqualTo(LogDataMasker.MASK_VALUE);
        assertThat(headers.get("Accept")).isEqualTo("application/json");
    }

    @Test
    void requestPayloadIsCapturedAndMasked() throws Exception {
        properties.getRestApi().setIncludeRequestPayload(true);
        MockHttpServletRequest request = request("POST", "/api/login");
        request.setContent("user=jo&password=hunter22".getBytes());
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        // chain must consume the request body for the caching wrapper to capture it
        filter.doFilter(request, response, (req, res) ->
                ((jakarta.servlet.http.HttpServletRequest) req).getInputStream().readAllBytes());

        String payload = (String) sink.last().getMetadata().get("requestPayload");
        assertThat(payload).contains("user=jo").doesNotContain("hunter22");
    }

    @Test
    void responsePayloadIsCapturedAndTruncated() throws Exception {
        properties.getRestApi().setIncludeResponsePayload(true);
        properties.getRestApi().setMaxPayloadLength(10);
        MockHttpServletRequest request = request("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            res.getOutputStream().write("this response is longer than ten chars".getBytes());
            ((jakarta.servlet.http.HttpServletResponse) res).setStatus(200);
        });

        String payload = (String) sink.last().getMetadata().get("responsePayload");
        assertThat(payload).endsWith("...[truncated]");
        assertThat(sink.last().getMetadata()).containsKey("responseSize");
        // body still reaches the client
        assertThat(response.getContentAsString()).startsWith("this response");
    }
}
