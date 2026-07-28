package com.adhar.kit.notification.config;

import com.adhar.kit.notification.InMemoryNotificationIdempotencyStore;
import com.adhar.kit.notification.InMemoryNotificationPreferenceStore;
import com.adhar.kit.notification.NotificationDigestService;
import com.adhar.kit.notification.NotificationHistory;
import com.adhar.kit.notification.NotificationIdempotencyStore;
import com.adhar.kit.notification.NotificationPreferenceStore;
import com.adhar.kit.notification.NotificationRateLimiter;
import com.adhar.kit.notification.NotificationRetryHandler;
import com.adhar.kit.notification.NotificationService;
import com.adhar.kit.notification.TemplateNotificationService;
import com.adhar.kit.notification.channel.InAppNotificationChannel;
import com.adhar.kit.notification.channel.NotificationChannel;
import com.adhar.kit.notification.channel.SmsNotificationChannel;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("NotificationAutoConfiguration")
class NotificationAutoConfigurationTest {

    private final NotificationAutoConfiguration config = new NotificationAutoConfiguration();

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @Test
    @DisplayName("preference store bean is the in-memory default")
    void preferenceStoreBean() {
        NotificationPreferenceStore store = config.notificationPreferenceStore();
        assertThat(store).isInstanceOf(InMemoryNotificationPreferenceStore.class);
    }

    @Test
    @DisplayName("rate limiter bean is built from properties")
    void rateLimiterBean() {
        NotificationProperties properties = new NotificationProperties();
        NotificationRateLimiter limiter = config.notificationRateLimiter(properties);
        assertThat(limiter.tryAcquire("u1", NotificationType.SMS)).isTrue();
    }

    @Test
    @DisplayName("idempotency store bean is the in-memory default")
    void idempotencyStoreBean() {
        NotificationProperties properties = new NotificationProperties();
        NotificationIdempotencyStore store = config.notificationIdempotencyStore(properties);
        assertThat(store).isInstanceOf(InMemoryNotificationIdempotencyStore.class);
        assertThat(store.register("k1")).isTrue();
    }

    @Test
    @DisplayName("digest service bean is built from properties")
    void digestServiceBean() {
        NotificationService service = mock(NotificationService.class);
        NotificationProperties properties = new NotificationProperties();
        try (NotificationDigestService digest = config.notificationDigestService(service, properties)) {
            assertThat(digest.pendingCount()).isZero();
        }
    }

    @Test
    @DisplayName("history, retry, in-app and sms channel beans are created")
    void supportingBeans() {
        NotificationProperties properties = new NotificationProperties();
        NotificationHistory history = config.notificationHistory(properties);
        assertThat(history.size()).isZero();
        try (NotificationRetryHandler retry = config.notificationRetryHandler(properties)) {
            assertThat(retry).isNotNull();
        }
        assertThat(config.inAppNotificationChannel()).isNotNull();
        assertThat(config.smsNotificationChannel()).isInstanceOf(SmsNotificationChannel.class);
    }

    @Test
    @DisplayName("notificationService bean routes to channels and honors guards")
    void notificationServiceBean() {
        NotificationProperties properties = new NotificationProperties();
        properties.setAsync(false);
        List<NotificationChannel> channels = List.of(new InAppNotificationChannel());

        NotificationService service = config.notificationService(
                channels, properties,
                provider(null), provider(null), provider(null), provider(null), provider(null));

        Notification n = new Notification("n1", NotificationType.IN_APP, "u1", "S", "B", null, null);
        assertThatCode(() -> service.send(n)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("template service bean is created with optional message source")
    void templateServiceBean() {
        NotificationService service = mock(NotificationService.class);
        TemplateNotificationService template = config.templateNotificationService(service, provider(null));
        assertThat(template.templateCount()).isZero();
    }

    @Test
    @DisplayName("logNotificationConfiguration runs without error")
    void logConfiguration() {
        assertThatCode(config::logNotificationConfiguration).doesNotThrowAnyException();
    }
}
