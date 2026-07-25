package com.adhar.kit.analytics.aspect;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.TrackEvent;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Aspect for processing @TrackEvent annotations.
 *
 * <p>Automatically tracks analytics events when annotated methods are executed.</p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(100)
@Slf4j
public class TrackEventAspect {

    private final AnalyticsFacade analytics;

    public TrackEventAspect() {
        this.analytics = AnalyticsFacade.getInstance();
    }

    /**
     * Package-private constructor for injecting a controllable
     * {@link AnalyticsFacade} (e.g. a Mockito mock) in tests, without going
     * through the process-wide singleton.
     */
    TrackEventAspect(AnalyticsFacade analytics) {
        this.analytics = analytics;
    }

    /**
     * Intercepts methods annotated with @TrackEvent.
     */
    @Around("@annotation(trackEvent)")
    public Object processTrackEvent(ProceedingJoinPoint joinPoint, TrackEvent trackEvent) throws Throwable {
        if (!analytics.isAvailable()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Get parameter map
        Map<String, Object> params = buildParameterMap(
            signature.getParameterNames(),
            joinPoint.getArgs()
        );

        // Get user ID
        String userId = extractUserId(params, trackEvent.userIdParam());
        if (userId == null) {
            log.warn("User ID not found for @TrackEvent on {}", method.getName());
            return joinPoint.proceed();
        }

        // Track on entry if configured
        if (trackEvent.trackOnEntry()) {
            trackEventAsync(userId, trackEvent, params, null, null);
        }

        Object result = null;
        Throwable exception = null;

        try {
            result = joinPoint.proceed();

            // Track on success
            if (trackEvent.trackOnSuccess()) {
                trackEventAsync(userId, trackEvent, params, result, null);
            }

            return result;

        } catch (Throwable t) {
            exception = t;

            // Track on failure
            if (trackEvent.trackOnFailure()) {
                trackEventAsync(userId, trackEvent, params, null, exception);
            }

            throw t;
        }
    }

    /**
     * Tracks event asynchronously if configured.
     */
    @Async
    protected void trackEventAsync(
        String userId,
        TrackEvent trackEvent,
        Map<String, Object> params,
        Object result,
        Throwable exception
    ) {
        if (trackEvent.async()) {
            doTrackEvent(userId, trackEvent, params, result, exception);
        } else {
            // Execute synchronously
            doTrackEvent(userId, trackEvent, params, result, exception);
        }
    }

    /**
     * Performs the actual event tracking.
     */
    private void doTrackEvent(
        String userId,
        TrackEvent trackEvent,
        Map<String, Object> params,
        Object result,
        Throwable exception
    ) {
        try {
            // Build event name
            String eventName = substituteParameters(trackEvent.event(), params);

            // Build properties
            Map<String, Object> properties = buildProperties(
                params,
                trackEvent.properties(),
                trackEvent.excludeProperties(),
                result,
                exception,
                trackEvent
            );

            // Defense-in-depth: redact configured/obvious PII from method
            // parameters before the event even reaches AnalyticsFacade (which
            // scrubs again on the send path).
            if (analytics.getPiiScrubber() != null) {
                properties = analytics.getPiiScrubber().scrub(properties);
            }

            log.debug("Tracking event: {} for user: {}", eventName, userId);
            analytics.track(userId, eventName, properties);

        } catch (Exception e) {
            log.error("Failed to track event", e);
        }
    }

    /**
     * Builds parameter map from method parameters.
     */
    private Map<String, Object> buildParameterMap(String[] paramNames, Object[] paramValues) {
        Map<String, Object> params = new HashMap<>();
        for (int i = 0; i < paramNames.length; i++) {
            params.put(paramNames[i], paramValues[i]);
        }
        return params;
    }

    /**
     * Extracts user ID from parameters.
     */
    private String extractUserId(Map<String, Object> params, String userIdParam) {
        Object userId = params.get(userIdParam);
        return userId != null ? userId.toString() : null;
    }

    /**
     * Substitutes {param} placeholders in template.
     */
    private String substituteParameters(String template, Map<String, Object> params) {
        String result = template;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "{" + entry.getKey() + "}";
            if (result.contains(placeholder) && entry.getValue() != null) {
                result = result.replace(placeholder, entry.getValue().toString());
            }
        }
        return result;
    }

    /**
     * Builds event properties from parameters.
     */
    private Map<String, Object> buildProperties(
        Map<String, Object> params,
        String[] includeProps,
        String[] excludeProps,
        Object result,
        Throwable exception,
        TrackEvent trackEvent
    ) {
        Map<String, Object> properties = new HashMap<>();
        Set<String> excludeSet = new HashSet<>(Arrays.asList(excludeProps));
        excludeSet.add(trackEvent.userIdParam()); // Don't include userId in properties

        if (includeProps.length == 0) {
            // Include all parameters except excluded ones
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!excludeSet.contains(entry.getKey()) && entry.getValue() != null) {
                    properties.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            // Include only specified parameters
            for (String prop : includeProps) {
                if (!excludeSet.contains(prop) && params.containsKey(prop)) {
                    properties.put(prop, params.get(prop));
                }
            }
        }

        // Include return value if configured
        if (trackEvent.includeReturnValue() && result != null) {
            properties.put(trackEvent.returnValueProperty(), result);
        }

        // Include exception info if present
        if (exception != null) {
            properties.put("error", exception.getClass().getSimpleName());
            properties.put("error_message", exception.getMessage());
        }

        return properties;
    }
}

