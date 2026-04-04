package com.adhar.kit.notification;

import com.adhar.kit.notification.channel.InAppNotificationChannel;
import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRetryHandler")
class NotificationRetryHandlerTest {

    @Mock
    private InAppNotificationChannel channel;

    private NotificationRetryHandler retryHandler;
    private Notification notification;

    @BeforeEach
    void setUp() {
        NotificationProperties properties = new NotificationProperties();
        properties.getRetry().setMaxRetries(3);
        properties.getRetry().setBackoffMs(10); // short backoff for tests
        retryHandler = new NotificationRetryHandler(properties);

        notification = new Notification(
                "notif-1", NotificationType.IN_APP, "user-1",
                "Subject", "Body", null, null
        );
    }

    @Test
    @DisplayName("successful send on first attempt returns true")
    void successfulSendOnFirstAttempt() {
        doNothing().when(channel).send(any(Notification.class));

        boolean result = retryHandler.retry(notification, channel, 3);

        assertThat(result).isTrue();
        verify(channel, times(1)).send(notification);
    }

    @Test
    @DisplayName("retry on failure up to maxRetries then succeeds")
    void retryOnFailureThenSucceeds() {
        doThrow(new RuntimeException("fail"))
                .doThrow(new RuntimeException("fail"))
                .doNothing()
                .when(channel).send(any(Notification.class));

        boolean result = retryHandler.retry(notification, channel, 3);

        assertThat(result).isTrue();
        verify(channel, times(3)).send(notification);
    }

    @Test
    @DisplayName("exponential backoff timing is applied between retries")
    void exponentialBackoffTiming() {
        doThrow(new RuntimeException("fail"))
                .doThrow(new RuntimeException("fail"))
                .doNothing()
                .when(channel).send(any(Notification.class));

        long start = System.currentTimeMillis();
        boolean result = retryHandler.retry(notification, channel, 3);
        long elapsed = System.currentTimeMillis() - start;

        assertThat(result).isTrue();
        // With backoffMs=10: first retry waits 10ms, second waits 20ms = 30ms minimum
        assertThat(elapsed).isGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("returns false when all retries exhausted")
    void returnsFalseWhenAllRetriesExhausted() {
        doThrow(new RuntimeException("persistent failure"))
                .when(channel).send(any(Notification.class));

        boolean result = retryHandler.retry(notification, channel, 2);

        assertThat(result).isFalse();
        // initial attempt + 2 retries = 3 total
        verify(channel, times(3)).send(notification);
    }
}
