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
}
