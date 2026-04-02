package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Webhook notification channel using Spring {@link WebClient}.
 * <p>
 * Sends notifications as HTTP POST requests to a webhook URL. The URL can be specified
 * per-notification via metadata key {@code "webhookUrl"}, or falls back to the configured
 * default URL ({@code adhar.notification.webhook.default-url}).
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class WebhookNotificationChannel implements NotificationChannel {

    private final WebClient webClient;
    private final NotificationProperties properties;

    public WebhookNotificationChannel(WebClient.Builder webClientBuilder, NotificationProperties properties) {
        this.webClient = webClientBuilder.build();
        this.properties = properties;
    }

    @Override
    public void send(Notification notification) {
        String url = resolveWebhookUrl(notification);
        int timeoutMs = properties.getWebhook().getTimeoutMs();

        log.debug("Sending webhook notification [id={}] to URL: {}", notification.id(), url);

        var payload = Map.of(
                "id", notification.id(),
                "type", notification.type().name(),
                "recipient", notification.recipient(),
                "subject", notification.subject(),
                "body", notification.body(),
                "metadata", notification.metadata(),
                "createdAt", notification.createdAt().toString()
        );

        webClient.post()
                .uri(url)
                .bodyValue(payload)
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofMillis(timeoutMs));

        log.info("Webhook notification [id={}] sent successfully to {}", notification.id(), url);
    }

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.WEBHOOK;
    }

    private String resolveWebhookUrl(Notification notification) {
        String url = notification.metadata().get("webhookUrl");
        if (url != null && !url.isBlank()) {
            return url;
        }
        String defaultUrl = properties.getWebhook().getDefaultUrl();
        if (defaultUrl != null && !defaultUrl.isBlank()) {
            return defaultUrl;
        }
        throw new IllegalArgumentException(
                "No webhook URL specified in notification metadata or default configuration for notification id=" + notification.id());
    }
}
