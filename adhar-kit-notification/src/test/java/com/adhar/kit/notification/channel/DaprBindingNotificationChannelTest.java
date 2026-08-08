package com.adhar.kit.notification.channel;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DaprBindingNotificationChannel} with a mocked {@link DaprFacade}.
 */
class DaprBindingNotificationChannelTest {

    private DaprFacade daprFacade;
    private NotificationProperties.DaprProperties config;
    private DaprBindingNotificationChannel channel;

    @BeforeEach
    void setUp() {
        daprFacade = mock(DaprFacade.class);
        config = new NotificationProperties.DaprProperties();
        channel = new DaprBindingNotificationChannel(daprFacade, config);
    }

    private Notification notification(NotificationType type, String recipient) {
        return new Notification("n-1", type, recipient, "Order Confirmed", "Order #123 confirmed",
                Map.of(), Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void supportsConfiguredTypesButNeverInApp() {
        assertThat(channel.supports(NotificationType.EMAIL)).isTrue();
        assertThat(channel.supports(NotificationType.SMS)).isTrue();
        assertThat(channel.supports(NotificationType.WEBHOOK)).isTrue();
        assertThat(channel.supports(NotificationType.IN_APP)).isFalse();
    }

    @Test
    void perTypeDisableFlagsAreHonored() {
        config.setEmailEnabled(false);
        config.setSmsBinding(" ");

        assertThat(channel.supports(NotificationType.EMAIL)).isFalse();
        assertThat(channel.supports(NotificationType.SMS)).isFalse();
        assertThat(channel.supports(NotificationType.WEBHOOK)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void emailUsesCreateOperationWithSmtpMetadata() {
        channel.send(notification(NotificationType.EMAIL, "user@example.com"));

        ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass((Class) Map.class);
        verify(daprFacade).invokeBinding(eq("smtp"), eq("create"), eq("Order #123 confirmed"),
                metadata.capture());
        assertThat(metadata.getValue())
                .containsEntry("emailTo", "user@example.com")
                .containsEntry("subject", "Order Confirmed");
    }

    @Test
    @SuppressWarnings("unchecked")
    void smsUsesCreateOperationWithToNumberMetadata() {
        channel.send(notification(NotificationType.SMS, "+15551234567"));

        ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass((Class) Map.class);
        verify(daprFacade).invokeBinding(eq("sms"), eq("create"), any(), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("toNumber", "+15551234567");
    }

    @Test
    @SuppressWarnings("unchecked")
    void webhookUsesPostOperationWithPathMetadata() {
        channel.send(notification(NotificationType.WEBHOOK, "/hooks/orders"));

        ArgumentCaptor<Map<String, String>> metadata = ArgumentCaptor.forClass((Class) Map.class);
        verify(daprFacade).invokeBinding(eq("webhook"), eq("post"), any(), metadata.capture());
        assertThat(metadata.getValue()).containsEntry("path", "/hooks/orders");
    }

    @Test
    void bindingFailurePropagates() {
        doThrow(new RuntimeException("binding down"))
                .when(daprFacade).invokeBinding(anyString(), anyString(), any(), anyMap());

        assertThatThrownBy(() -> channel.send(notification(NotificationType.EMAIL, "u@e.com")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("binding down");
    }

    @Test
    void inAppSendIsRejected() {
        assertThatThrownBy(() -> channel.send(notification(NotificationType.IN_APP, "user-1")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
