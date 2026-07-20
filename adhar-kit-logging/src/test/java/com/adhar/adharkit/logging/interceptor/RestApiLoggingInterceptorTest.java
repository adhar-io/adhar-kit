package com.adhar.adharkit.logging.interceptor;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import com.adhar.adharkit.logging.event.RecordingAppLogEventSink;
import com.adhar.adharkit.logging.masking.LogDataMasker;
import com.adhar.adharkit.logging.properties.AdharLoggingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RestApiLoggingInterceptor}.
 */
class RestApiLoggingInterceptorTest {

    private RecordingAppLogEventSink sink;
    private RestApiLoggingInterceptor interceptor;

    static class SampleController {
        @SuppressWarnings("unused")
        public String getOrders() {
            return "orders";
        }
    }

    @BeforeEach
    void setUp() {
        AdharLoggingProperties properties = new AdharLoggingProperties();
        sink = new RecordingAppLogEventSink();
        interceptor = new RestApiLoggingInterceptor(new AppLogEventPublisher(properties,
                new LogDataMasker(properties.getMasking()), List.of(sink)));
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    private HandlerMethod handlerMethod() throws NoSuchMethodException {
        Method method = SampleController.class.getMethod("getOrders");
        return new HandlerMethod(new SampleController(), method);
    }

    @Test
    void preHandlePublishesHandlerNameToMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");

        boolean proceed = interceptor.preHandle(request, new MockHttpServletResponse(), handlerMethod());

        assertThat(proceed).isTrue();
        assertThat(MDC.get(RestApiLoggingInterceptor.HANDLER_MDC_KEY))
                .isEqualTo("SampleController.getOrders");
    }

    @Test
    void afterCompletionPublishesHandlerEventAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);
        HandlerMethod handler = handlerMethod();

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, null);

        AppLogEvent event = sink.last();
        assertThat(event.getType()).isEqualTo(AppLogEventType.API);
        assertThat(event.getCategory()).isEqualTo("http-handler");
        assertThat(event.getName()).isEqualTo("SampleController.getOrders");
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.SUCCESS);
        assertThat(event.getDurationMs()).isNotNull();
        assertThat(event.getMetadata()).containsEntry("status", 200);
        assertThat(MDC.get(RestApiLoggingInterceptor.HANDLER_MDC_KEY)).isNull();
    }

    @Test
    void exceptionResultsInFailureEvent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(500);
        HandlerMethod handler = handlerMethod();

        interceptor.preHandle(request, response, handler);
        interceptor.afterCompletion(request, response, handler, new IllegalStateException("boom"));

        AppLogEvent event = sink.last();
        assertThat(event.getOutcome()).isEqualTo(AppLogEventOutcome.FAILURE);
        assertThat(event.getSeverity()).isEqualTo(Level.ERROR);
        assertThat(event.getErrorMessage()).isEqualTo("boom");
    }

    @Test
    void nonHandlerMethodHandlersAreIgnored() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/static/app.js");
        MockHttpServletResponse response = new MockHttpServletResponse();
        Object resourceHandler = new Object();

        interceptor.preHandle(request, response, resourceHandler);
        interceptor.afterCompletion(request, response, resourceHandler, null);

        assertThat(sink.getEvents()).isEmpty();
        assertThat(MDC.get(RestApiLoggingInterceptor.HANDLER_MDC_KEY)).isNull();
    }
}
