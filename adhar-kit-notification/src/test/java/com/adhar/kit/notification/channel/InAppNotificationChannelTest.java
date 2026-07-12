package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("InAppNotificationChannel")
class InAppNotificationChannelTest {

    private final InAppNotificationChannel channel = new InAppNotificationChannel();

    @Test
    @DisplayName("supports only IN_APP type")
    void supports() {
        assertThat(channel.supports(NotificationType.IN_APP)).isTrue();
        assertThat(channel.supports(NotificationType.EMAIL)).isFalse();
        assertThat(channel.supports(NotificationType.SMS)).isFalse();
        assertThat(channel.supports(NotificationType.WEBHOOK)).isFalse();
    }

    @Test
    @DisplayName("send logs the in-app notification without error")
    void send() {
        Notification n = new Notification("a1", NotificationType.IN_APP, "user-9",
                "Title", "Message", Map.of(), null);

        assertThatCode(() -> channel.send(n)).doesNotThrowAnyException();
    }
}
