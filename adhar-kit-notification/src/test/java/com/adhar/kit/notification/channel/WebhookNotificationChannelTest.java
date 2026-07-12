package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("WebhookNotificationChannel")
class WebhookNotificationChannelTest {

    private NotificationProperties propertiesWithDefault(String defaultUrl) {
        NotificationProperties p = new NotificationProperties();
        p.getWebhook().setDefaultUrl(defaultUrl);
        p.getWebhook().setTimeoutMs(2000);
        return p;
    }

    private WebClient.Builder builder(ExchangeFunction exchange) {
        return WebClient.builder().exchangeFunction(exchange);
    }

    @Test
    @DisplayName("supports only WEBHOOK type")
    void supports() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel(
                builder(req -> Mono.empty()), propertiesWithDefault(null));
        assertThat(channel.supports(NotificationType.WEBHOOK)).isTrue();
        assertThat(channel.supports(NotificationType.EMAIL)).isFalse();
        assertThat(channel.supports(NotificationType.SMS)).isFalse();
        assertThat(channel.supports(NotificationType.IN_APP)).isFalse();
    }

    @Test
    @DisplayName("send posts to the per-notification webhookUrl from metadata")
    void sendToMetadataUrl() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchange = req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };
        WebhookNotificationChannel channel = new WebhookNotificationChannel(
                builder(exchange), propertiesWithDefault(null));

        Notification n = new Notification("w1", NotificationType.WEBHOOK, "ignored",
                "Subj", "Payload", Map.of("webhookUrl", "https://hook.test/a"), null);

        assertThatCode(() -> channel.send(n)).doesNotThrowAnyException();
        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().url().toString()).isEqualTo("https://hook.test/a");
        assertThat(captured.get().method().name()).isEqualTo("POST");
    }

    @Test
    @DisplayName("send falls back to the configured default URL when no metadata url present")
    void sendToDefaultUrl() {
        AtomicReference<ClientRequest> captured = new AtomicReference<>();
        ExchangeFunction exchange = req -> {
            captured.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).build());
        };
        WebhookNotificationChannel channel = new WebhookNotificationChannel(
                builder(exchange), propertiesWithDefault("https://default.test/hook"));

        Notification n = new Notification("w2", NotificationType.WEBHOOK, "x",
                "S", "B", Map.of(), null);

        channel.send(n);
        assertThat(captured.get().url().toString()).isEqualTo("https://default.test/hook");
    }

    @Test
    @DisplayName("send throws IllegalArgumentException when no url is resolvable")
    void sendNoUrl() {
        WebhookNotificationChannel channel = new WebhookNotificationChannel(
                builder(req -> Mono.just(ClientResponse.create(HttpStatus.OK).build())),
                propertiesWithDefault(null));

        Notification n = new Notification("w3", NotificationType.WEBHOOK, "x",
                "S", "B", Map.of(), null);

        assertThatThrownBy(() -> channel.send(n))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No webhook URL specified");
    }

    @Test
    @DisplayName("send propagates error when the server responds with 5xx")
    void sendServerError() {
        ExchangeFunction exchange = req ->
                Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build());
        WebhookNotificationChannel channel = new WebhookNotificationChannel(
                builder(exchange), propertiesWithDefault("https://default.test/hook"));

        Notification n = new Notification("w4", NotificationType.WEBHOOK, "x",
                "S", "B", Map.of(), null);

        assertThatThrownBy(() -> channel.send(n))
                .isInstanceOf(WebClientResponseException.class);
    }
}
