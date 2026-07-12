package com.adhar.kit.notification.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationEvent")
class NotificationEventTest {

    @Test
    @DisplayName("success factory produces a successful event with null error")
    void successFactory() {
        NotificationEvent event = NotificationEvent.success("id-1", "EMAIL", "a@b.com");

        assertThat(event.notificationId()).isEqualTo("id-1");
        assertThat(event.type()).isEqualTo("EMAIL");
        assertThat(event.recipient()).isEqualTo("a@b.com");
        assertThat(event.success()).isTrue();
        assertThat(event.errorMessage()).isNull();
        assertThat(event.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("failure factory produces a failed event with error message")
    void failureFactory() {
        NotificationEvent event = NotificationEvent.failure("id-2", "SMS", "+1", "timeout");

        assertThat(event.notificationId()).isEqualTo("id-2");
        assertThat(event.type()).isEqualTo("SMS");
        assertThat(event.recipient()).isEqualTo("+1");
        assertThat(event.success()).isFalse();
        assertThat(event.errorMessage()).isEqualTo("timeout");
        assertThat(event.timestamp()).isNotNull();
    }
}
