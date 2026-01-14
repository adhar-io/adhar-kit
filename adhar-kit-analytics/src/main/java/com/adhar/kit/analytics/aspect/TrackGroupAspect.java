package com.adhar.kit.analytics.aspect;

import com.adhar.kit.analytics.AnalyticsFacade;
import com.adhar.kit.analytics.annotation.*;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Aspect for processing @TrackGroup annotation.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Aspect
@Component
@Order(100)
@Slf4j
public class TrackGroupAspect {

    private final AnalyticsFacade analytics;

    public TrackGroupAspect() {
        this.analytics = AnalyticsFacade.getInstance();
    }

    @Around("@annotation(trackGroup)")
    public Object processTrackGroup(ProceedingJoinPoint joinPoint, TrackGroup trackGroup) throws Throwable {
        if (!analytics.isAvailable()) {
            return joinPoint.proceed();
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Map<String, Object> params = buildParameterMap(
            signature.getParameterNames(),
            joinPoint.getArgs()
        );

        String userId = extractParam(params, trackGroup.userIdParam());
        String groupId = extractParam(params, trackGroup.groupIdParam());

        if (userId != null && groupId != null) {
            Map<String, Object> properties = buildProperties(params, trackGroup);

            log.debug("Adding user {} to group {} (type: {})", userId, groupId, trackGroup.groupType());
            analytics.group(userId, groupId, properties);

            if (trackGroup.trackEvent()) {
                analytics.track(userId, trackGroup.eventName(), Map.of(
                    "group_id", groupId,
                    "group_type", trackGroup.groupType()
                ));
            }
        }

        return joinPoint.proceed();
    }

    private Map<String, Object> buildParameterMap(String[] names, Object[] values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < names.length; i++) {
            map.put(names[i], values[i]);
        }
        return map;
    }

    private String extractParam(Map<String, Object> params, String paramName) {
        Object value = params.get(paramName);
        return value != null ? value.toString() : null;
    }

    private Map<String, Object> buildProperties(Map<String, Object> params, TrackGroup trackGroup) {
        Map<String, Object> properties = new HashMap<>();
        properties.put("$group_type", trackGroup.groupType());

        Set<String> excludeSet = new HashSet<>(Arrays.asList(trackGroup.excludeProperties()));
        excludeSet.add(trackGroup.userIdParam());
        excludeSet.add(trackGroup.groupIdParam());

        if (trackGroup.properties().length == 0) {
            // Include all except excluded
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (!excludeSet.contains(entry.getKey()) && entry.getValue() != null) {
                    properties.put(entry.getKey(), entry.getValue());
                }
            }
        } else {
            // Include only specified
            for (String prop : trackGroup.properties()) {
                if (!excludeSet.contains(prop) && params.containsKey(prop)) {
                    properties.put(prop, params.get(prop));
                }
            }
        }

        return properties;
    }
}

