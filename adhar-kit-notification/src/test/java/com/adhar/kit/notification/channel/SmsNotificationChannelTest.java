package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("SmsNotificationChannel")
class SmsNotificationChannelTest {

    private final SmsNotificationChannel channel = new SmsNotificationChannel();

    @Test
    @DisplayName("supports only SMS type")
    void supports() {
        assertThat(channel.supports(NotificationType.SMS)).isTrue();
        assertThat(channel.supports(NotificationType.EMAIL)).isFalse();
        assertThat(channel.supports(NotificationType.WEBHOOK)).isFalse();
        assertThat(channel.supports(NotificationType.IN_APP)).isFalse();
    }

    @Test
    @DisplayName("send uses default provider when none specified")
    void sendDefaultProvider() {
        Notification n = new Notification("s1", NotificationType.SMS, "+100",
                "S", "B", Map.of(), null);

        assertThatCode(() -> channel.send(n)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("send uses custom provider from metadata")
    void sendCustomProvider() {
        Notification n = new Notification("s2", NotificationType.SMS, "+200",
                "S", "B", Map.of("smsProvider", "twilio"), null);

        assertThatCode(() -> channel.send(n)).doesNotThrowAnyException();
    }
}
