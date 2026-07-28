package com.adhar.kit.notification;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationRateLimiter")
class NotificationRateLimiterTest {

    private NotificationProperties.RateLimitProperties props(int maxPerWindow, long windowMs) {
        NotificationProperties.RateLimitProperties p = new NotificationProperties.RateLimitProperties();
        p.setMaxPerWindow(maxPerWindow);
        p.setWindowMs(windowMs);
        return p;
    }

    @Test
    @DisplayName("permits up to the configured maximum within the window then rejects")
    void permitsUpToMax() {
        AtomicLong now = new AtomicLong(0);
        NotificationRateLimiter limiter = new NotificationRateLimiter(props(2, 1000), now::get);

        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isTrue();
        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isTrue();
        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isFalse();
        assertThat(limiter.currentCount("u1", NotificationType.SMS)).isEqualTo(2);
    }

    @Test
    @DisplayName("permits again once earlier timestamps slide out of the window")
    void windowSlides() {
        AtomicLong now = new AtomicLong(0);
        NotificationRateLimiter limiter = new NotificationRateLimiter(props(1, 1000), now::get);

        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isTrue();
        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isFalse();

        now.set(1000); // first timestamp now expired
        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isTrue();
    }

    @Test
    @DisplayName("tracks separate windows per recipient and channel")
    void isolatesKeys() {
        AtomicLong now = new AtomicLong(0);
        NotificationRateLimiter limiter = new NotificationRateLimiter(props(1, 1000), now::get);

        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isTrue();
        assertThat(limiter.tryAcquire("u2", NotificationType.SMS)).isTrue();
        assertThat(limiter.tryAcquire("u1", NotificationType.EMAIL)).isTrue();
        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isFalse();
    }

    @Test
    @DisplayName("currentCount returns zero for an unknown key")
    void currentCountUnknownKey() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(props(5, 1000));
        assertThat(limiter.currentCount("nobody", NotificationType.SMS)).isZero();
    }

    @Test
    @DisplayName("reset clears all tracked windows")
    void resetClears() {
        AtomicLong now = new AtomicLong(0);
        NotificationRateLimiter limiter = new NotificationRateLimiter(props(1, 1000), now::get);
        limiter.tryAcquire("u1", NotificationType.SMS);

        limiter.reset();

        assertThat(limiter.currentCount("u1", NotificationType.SMS)).isZero();
        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isTrue();
    }

    @Test
    @DisplayName("default constructor uses the system clock")
    void defaultConstructor() {
        NotificationRateLimiter limiter = new NotificationRateLimiter(props(1, 60000));
        assertThat(limiter.tryAcquire("u1", NotificationType.IN_APP)).isTrue();
        assertThat(limiter.tryAcquire("u1", NotificationType.IN_APP)).isFalse();
    }
}
