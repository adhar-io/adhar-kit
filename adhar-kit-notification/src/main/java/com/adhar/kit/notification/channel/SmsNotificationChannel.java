package com.adhar.kit.notification.channel;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * SMS notification channel backed by a configurable HTTP REST gateway.
 * <p>
 * When {@code adhar.notification.sms.url} is configured, notifications are delivered
 * to the gateway via {@link HttpClient} using the configured method, auth header, and
 * payload template. Placeholders {@code ${recipient}}, {@code ${subject}}, {@code ${body}},
 * {@code ${id}}, and {@code ${metadata.<key>}} are substituted into the payload template;
 * values are JSON-escaped when the content type is JSON.
 * </p>
 * <p>
 * When no gateway URL is configured, the channel falls back to logging the SMS details,
 * preserving the previous stub behavior. Provider-specific integrations (e.g., Twilio,
 * AWS SNS, Vonage) can still be plugged in by consuming applications via their own
 * {@link NotificationChannel} bean supporting {@link NotificationType#SMS}.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public final class SmsNotificationChannel implements NotificationChannel {

    private final NotificationProperties.SmsProperties smsProperties;
    private final HttpClient httpClient;

    /**
     * Creates an unconfigured channel that falls back to logging.
     */
    public SmsNotificationChannel() {
        this(new NotificationProperties().getSms());
    }

    /**
     * Creates a channel with the given SMS properties, building an {@link HttpClient}
     * when a gateway URL is configured.
     *
     * @param smsProperties the SMS channel configuration
     */
    public SmsNotificationChannel(NotificationProperties.SmsProperties smsProperties) {
        this(smsProperties, isConfigured(smsProperties)
                ? HttpClient.newBuilder()
                        .connectTimeout(Duration.ofMillis(smsProperties.getTimeoutMs()))
                        .build()
                : null);
    }

    /**
     * Creates a channel with the given SMS properties and HTTP client.
     *
     * @param smsProperties the SMS channel configuration
     * @param httpClient    the HTTP client used to call the gateway (may be {@code null} for fallback mode)
     */
    public SmsNotificationChannel(NotificationProperties.SmsProperties smsProperties, HttpClient httpClient) {
        this.smsProperties = smsProperties;
        this.httpClient = httpClient;
    }

    @Override
    public void send(Notification notification) {
        if (!isConfigured(smsProperties) || httpClient == null) {
            logFallback(notification);
            return;
        }
        sendViaGateway(notification);
    }

    @Override
    public boolean supports(NotificationType type) {
        return type == NotificationType.SMS;
    }

    private static boolean isConfigured(NotificationProperties.SmsProperties properties) {
        return properties != null && properties.getUrl() != null && !properties.getUrl().isBlank();
    }

    private void logFallback(Notification notification) {
        String phoneNumber = notification.recipient();
        String provider = notification.metadata().getOrDefault("smsProvider", "default");

        log.info("SMS notification [id={}] to '{}' via provider '{}': subject='{}', body='{}'",
                notification.id(),
                phoneNumber,
                provider,
                notification.subject(),
                notification.body());
    }

    private void sendViaGateway(Notification notification) {
        String payload = renderPayload(notification);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(smsProperties.getUrl()))
                .timeout(Duration.ofMillis(smsProperties.getTimeoutMs()))
                .header("Content-Type", smsProperties.getContentType())
                .method(smsProperties.getMethod().toUpperCase(Locale.ROOT),
                        HttpRequest.BodyPublishers.ofString(payload));

        if (smsProperties.getAuthHeaderValue() != null && !smsProperties.getAuthHeaderValue().isBlank()) {
            requestBuilder.header(smsProperties.getAuthHeaderName(), smsProperties.getAuthHeaderValue());
        }

        try {
            HttpResponse<String> response =
                    httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("SMS gateway returned status " + status
                        + " for notification [id=" + notification.id() + "]");
            }
            log.info("SMS notification [id={}] delivered to '{}' via gateway '{}' (status={})",
                    notification.id(), notification.recipient(), smsProperties.getUrl(), status);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to send SMS notification [id="
                    + notification.id() + "] via gateway: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending SMS notification [id="
                    + notification.id() + "]", e);
        }
    }

    /**
     * Renders the configured payload template for the given notification.
     *
     * @param notification the notification providing placeholder values
     * @return the rendered payload
     */
    String renderPayload(Notification notification) {
        boolean json = smsProperties.getContentType() != null
                && smsProperties.getContentType().toLowerCase(Locale.ROOT).contains("json");

        Map<String, String> values = new HashMap<>();
        values.put("recipient", nullSafe(notification.recipient()));
        values.put("subject", nullSafe(notification.subject()));
        values.put("body", nullSafe(notification.body()));
        values.put("id", nullSafe(notification.id()));
        notification.metadata().forEach((key, value) -> values.put("metadata." + key, nullSafe(value)));

        String result = smsProperties.getPayloadTemplate();
        for (var entry : values.entrySet()) {
            String replacement = json ? escapeJson(entry.getValue()) : entry.getValue();
            result = result.replace("${" + entry.getKey() + "}", replacement);
        }
        return result;
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static String escapeJson(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
