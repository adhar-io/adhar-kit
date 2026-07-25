package com.adhar.kit.ai.component;

import com.adhar.kit.ai.config.AiProperties;
import com.adhar.kit.commons.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate limiting component for AI services.
 * Implements sliding window rate limiting with configurable limits.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRateLimiter {

    private final AiProperties aiProperties;
    private final Map<String, UserRateLimit> userLimits = new ConcurrentHashMap<>();

    /**
     * Checks if request is allowed for the given user/tenant.
     */
    public boolean isAllowed(String identifier) {
        if (!aiProperties.getRateLimiting().getEnabled()) {
            return true;
        }

        UserRateLimit rateLimit = userLimits.computeIfAbsent(identifier,
            k -> new UserRateLimit());

        return rateLimit.isAllowed();
    }

    /**
     * Records a request for the given user/tenant.
     */
    public void recordRequest(String identifier) {
        if (!aiProperties.getRateLimiting().getEnabled()) {
            return;
        }

        UserRateLimit rateLimit = userLimits.get(identifier);
        if (rateLimit != null) {
            rateLimit.recordRequest();
        }
    }

    /**
     * Gets current usage for identifier.
     */
    public RateLimitStatus getStatus(String identifier) {
        UserRateLimit rateLimit = userLimits.get(identifier);
        if (rateLimit == null) {
            return new RateLimitStatus(0, 0, 0, LocalDateTime.now());
        }

        return new RateLimitStatus(
            rateLimit.minuteCount.get(),
            rateLimit.hourCount.get(),
            rateLimit.dayCount.get(),
            rateLimit.lastDayReset
        );
    }

    /**
     * Throws exception if rate limit exceeded.
     */
    public void checkRateLimit(String identifier) {
        if (!isAllowed(identifier)) {
            RateLimitStatus status = getStatus(identifier);
            throw new ServiceException("RATE_LIMIT_EXCEEDED",
                String.format("Rate limit exceeded. Current usage: %d requests per minute",
                    status.minuteRequests()));
        }
        recordRequest(identifier);
    }

    private class UserRateLimit {
        private final AtomicInteger minuteCount = new AtomicInteger(0);
        private final AtomicInteger hourCount = new AtomicInteger(0);
        private final AtomicInteger dayCount = new AtomicInteger(0);

        // Each window tracks its own "last reset" instant. Using a single shared
        // timestamp (the original implementation) meant that once one minute had
        // elapsed since object creation, Duration.between(lastReset, now) kept
        // growing forever, so minuteCount (and eventually hourCount) was wiped
        // back to zero on *every* subsequent check - effectively disabling rate
        // limiting for any process that had been running for more than a minute/hour.
        private volatile LocalDateTime lastMinuteReset = LocalDateTime.now();
        private volatile LocalDateTime lastHourReset = LocalDateTime.now();
        private volatile LocalDateTime lastDayReset = LocalDateTime.now();

        boolean isAllowed() {
            cleanup();

            int minuteLimit = aiProperties.getRateLimiting().getRequestsPerMinute();
            int hourLimit = aiProperties.getRateLimiting().getRequestsPerHour();
            int dayLimit = aiProperties.getRateLimiting().getRequestsPerDay();

            return minuteCount.get() < minuteLimit &&
                   hourCount.get() < hourLimit &&
                   dayCount.get() < dayLimit;
        }

        void recordRequest() {
            minuteCount.incrementAndGet();
            hourCount.incrementAndGet();
            dayCount.incrementAndGet();
        }

        private synchronized void cleanup() {
            LocalDateTime now = LocalDateTime.now();

            // Reset minute counter only once a full minute has actually elapsed
            // since the *last minute reset*, then advance that window's marker.
            if (Duration.between(lastMinuteReset, now).toMinutes() >= 1) {
                minuteCount.set(0);
                lastMinuteReset = now;
            }

            // Reset hour counter only once a full hour has elapsed since the
            // last hour reset.
            if (Duration.between(lastHourReset, now).toHours() >= 1) {
                hourCount.set(0);
                lastHourReset = now;
            }

            // Reset day counter only once a full day has elapsed since the
            // last day reset.
            if (Duration.between(lastDayReset, now).toDays() >= 1) {
                dayCount.set(0);
                lastDayReset = now;
            }
        }
    }

    public record RateLimitStatus(
        int minuteRequests,
        int hourRequests,
        int dayRequests,
        LocalDateTime lastReset
    ) {}
}
