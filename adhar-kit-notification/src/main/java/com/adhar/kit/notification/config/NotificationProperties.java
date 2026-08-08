package com.adhar.kit.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Adhar Notification module.
 *
 * <p><b>Example - application.yml:</b></p>
 * <pre>{@code
 * adhar:
 *   notification:
 *     enabled: true
 *     async: true
 *     email:
 *       enabled: true
 *       from: noreply@example.com
 *       template-path: classpath:templates/
 *     webhook:
 *       enabled: true
 *       default-url: https://hooks.example.com/notify
 *       timeout-ms: 5000
 *     in-app:
 *       enabled: true
 * }</pre>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "adhar.notification")
public class NotificationProperties {

    /**
     * Whether the notification module is enabled.
     */
    private boolean enabled = true;

    /**
     * Whether notifications should be sent asynchronously by default.
     */
    private boolean async = true;

    /**
     * Email channel configuration.
     */
    private EmailProperties email = new EmailProperties();

    /**
     * Webhook channel configuration.
     */
    private WebhookProperties webhook = new WebhookProperties();

    /**
     * In-app channel configuration.
     */
    private InAppProperties inApp = new InAppProperties();

    /**
     * SMS channel configuration.
     */
    private SmsProperties sms = new SmsProperties();

    /**
     * Retry configuration for failed notification sends.
     */
    private RetryProperties retry = new RetryProperties();

    /**
     * Notification history configuration.
     */
    private HistoryProperties history = new HistoryProperties();

    /**
     * Per-recipient/channel rate limiting configuration.
     */
    private RateLimitProperties rateLimit = new RateLimitProperties();

    /**
     * Idempotency (duplicate suppression) configuration.
     */
    private IdempotencyProperties idempotency = new IdempotencyProperties();

    /**
     * Digest/batching configuration.
     */
    private DigestProperties digest = new DigestProperties();

    /**
     * Configuration for the Dapr output-binding channel.
     */
    private DaprProperties dapr = new DaprProperties();

    /**
     * Email notification properties.
     */
    @Data
    public static class EmailProperties {

        /**
         * Whether the email channel is enabled.
         */
        private boolean enabled = false;

        /**
         * Default sender email address.
         */
        private String from;

        /**
         * Path to email templates.
         */
        private String templatePath = "classpath:templates/";
    }

    /**
     * Webhook notification properties.
     */
    @Data
    public static class WebhookProperties {

        /**
         * Whether the webhook channel is enabled.
         */
        private boolean enabled = false;

        /**
         * Default webhook URL for sending notifications.
         */
        private String defaultUrl;

        /**
         * Timeout in milliseconds for webhook HTTP calls.
         */
        private int timeoutMs = 5000;
    }

    /**
     * In-app notification properties.
     */
    @Data
    public static class InAppProperties {

        /**
         * Whether the in-app channel is enabled.
         */
        private boolean enabled = true;
    }

    /**
     * SMS notification properties.
     * <p>
     * When {@code url} is configured, SMS notifications are delivered to a generic
     * REST gateway via HTTP. When left unset, the SMS channel falls back to logging.
     * </p>
     */
    @Data
    public static class SmsProperties {

        /**
         * Whether the SMS channel is enabled.
         */
        private boolean enabled = false;

        /**
         * SMS gateway endpoint URL. When unset, SMS sends fall back to logging.
         */
        private String url;

        /**
         * HTTP method used when calling the SMS gateway.
         */
        private String method = "POST";

        /**
         * Name of the authentication header sent to the SMS gateway.
         */
        private String authHeaderName = "Authorization";

        /**
         * Value of the authentication header (e.g. "Bearer &lt;token&gt;"). Optional.
         */
        private String authHeaderValue;

        /**
         * Content type of the gateway request payload.
         */
        private String contentType = "application/json";

        /**
         * Payload template with {@code ${recipient}}, {@code ${subject}}, {@code ${body}},
         * {@code ${id}} and {@code ${metadata.<key>}} placeholders.
         */
        private String payloadTemplate = "{\"to\":\"${recipient}\",\"message\":\"${body}\"}";

        /**
         * Timeout in milliseconds for SMS gateway HTTP calls.
         */
        private int timeoutMs = 5000;
    }

    /**
     * Dapr output-binding channel configuration ({@code adhar.notification.dapr.*}).
     * Only relevant when the optional {@code adhar-kit-dapr} module is on the
     * classpath and {@code adhar.dapr.enabled=true}.
     */
    @Data
    public static class DaprProperties {

        /**
         * Whether the Dapr binding channel is registered at all (in addition to
         * the Dapr module's own {@code adhar.dapr.enabled} switch).
         */
        private boolean enabled = true;

        /**
         * Dapr output binding used for EMAIL notifications (e.g. an SMTP or
         * SendGrid component).
         */
        private String emailBinding = "smtp";

        /**
         * Whether EMAIL delivery through Dapr is enabled.
         */
        private boolean emailEnabled = true;

        /**
         * Dapr output binding used for SMS notifications (e.g. a Twilio component).
         */
        private String smsBinding = "sms";

        /**
         * Whether SMS delivery through Dapr is enabled.
         */
        private boolean smsEnabled = true;

        /**
         * Dapr HTTP output binding used for WEBHOOK notifications.
         */
        private String httpBinding = "webhook";

        /**
         * Whether WEBHOOK delivery through Dapr is enabled.
         */
        private boolean webhookEnabled = true;
    }

    /**
     * Retry configuration for failed notification sends.
     */
    @Data
    public static class RetryProperties {

        /**
         * Maximum number of retry attempts for failed sends.
         */
        private int maxRetries = 3;

        /**
         * Base backoff delay in milliseconds between retries (exponential backoff applied).
         */
        private long backoffMs = 1000;

        /**
         * Upper bound in milliseconds for the exponential backoff delay.
         */
        private long maxBackoffMs = 30000;
    }

    /**
     * Notification history configuration.
     */
    @Data
    public static class HistoryProperties {

        /**
         * Maximum number of notification history entries to retain.
         */
        private int maxSize = 1000;
    }

    /**
     * Per-recipient/channel sliding-window rate limiting configuration.
     */
    @Data
    public static class RateLimitProperties {

        /**
         * Whether rate limiting is enabled.
         */
        private boolean enabled = false;

        /**
         * Maximum number of notifications per recipient/channel within the window.
         */
        private int maxPerWindow = 60;

        /**
         * Sliding window length in milliseconds.
         */
        private long windowMs = 60000;
    }

    /**
     * Idempotency-key duplicate suppression configuration.
     */
    @Data
    public static class IdempotencyProperties {

        /**
         * Whether idempotency-key duplicate suppression is enabled.
         */
        private boolean enabled = true;

        /**
         * Time-to-live in milliseconds for remembered idempotency keys.
         */
        private long ttlMs = 86400000;
    }

    /**
     * Digest/batching configuration for aggregating notifications per recipient.
     */
    @Data
    public static class DigestProperties {

        /**
         * Whether the digest service is enabled.
         */
        private boolean enabled = false;

        /**
         * Aggregation window in milliseconds before a digest is flushed.
         */
        private long windowMs = 60000;

        /**
         * Maximum number of notifications aggregated before an immediate flush.
         */
        private int maxBatchSize = 50;

        /**
         * Subject template for digest notifications ({@code ${count}} placeholder supported).
         */
        private String subjectTemplate = "You have ${count} new notifications";
    }
}
