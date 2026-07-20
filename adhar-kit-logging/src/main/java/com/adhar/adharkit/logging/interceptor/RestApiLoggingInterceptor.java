package com.adhar.adharkit.logging.interceptor;

import com.adhar.adharkit.logging.event.AppLogEvent;
import com.adhar.adharkit.logging.event.AppLogEventOutcome;
import com.adhar.adharkit.logging.event.AppLogEventPublisher;
import com.adhar.adharkit.logging.event.AppLogEventType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Spring MVC {@link HandlerInterceptor} that tracks controller handler execution.
 *
 * <p>Complements {@link com.adhar.adharkit.logging.filter.RestApiLoggingFilter} (which measures
 * the full request, including the filter chain) by attributing the time spent inside the resolved
 * controller method. On {@code preHandle} it publishes the handler name
 * ({@code Controller.method}) to the MDC under the {@code handler} key so all logs written while
 * handling the request carry it, and on {@code afterCompletion} it publishes an
 * {@link AppLogEventType#API} event named after the handler with duration, status and any
 * unhandled exception.</p>
 *
 * <p>Disabled via {@code adhar.logging.rest-api.interceptor-enabled=false}.</p>
 */
public class RestApiLoggingInterceptor implements HandlerInterceptor {

    /** MDC key under which the resolved handler name is published. */
    public static final String HANDLER_MDC_KEY = "handler";

    private static final String START_TIME_ATTRIBUTE = RestApiLoggingInterceptor.class.getName() + ".start";
    private static final String HANDLER_NAME_ATTRIBUTE = RestApiLoggingInterceptor.class.getName() + ".handler";

    private final AppLogEventPublisher publisher;

    /**
     * Creates the interceptor.
     *
     * @param publisher event pipeline
     */
    public RestApiLoggingInterceptor(AppLogEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START_TIME_ATTRIBUTE, System.nanoTime());
        String handlerName = handlerName(handler);
        if (handlerName != null) {
            request.setAttribute(HANDLER_NAME_ATTRIBUTE, handlerName);
            MDC.put(HANDLER_MDC_KEY, handlerName);
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        try {
            Object start = request.getAttribute(START_TIME_ATTRIBUTE);
            String handlerName = (String) request.getAttribute(HANDLER_NAME_ATTRIBUTE);
            if (start instanceof Long startNanos && handlerName != null) {
                long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
                boolean failed = ex != null || response.getStatus() >= 500;
                publisher.publish(AppLogEvent.builder()
                        .type(AppLogEventType.API)
                        .category("http-handler")
                        .name(handlerName)
                        .outcome(failed ? AppLogEventOutcome.FAILURE : AppLogEventOutcome.SUCCESS)
                        .severity(failed ? Level.ERROR : Level.DEBUG)
                        .durationMs(durationMs)
                        .error(ex)
                        .metadata("method", request.getMethod())
                        .metadata("path", request.getRequestURI())
                        .metadata("status", response.getStatus())
                        .build());
            }
        } finally {
            MDC.remove(HANDLER_MDC_KEY);
        }
    }

    private String handlerName(Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            return handlerMethod.getBeanType().getSimpleName() + "." + handlerMethod.getMethod().getName();
        }
        return null;
    }
}
