package com.adhar.kit.notification.model;

/**
 * Enumeration of supported notification channel types.
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public enum NotificationType {

    /** Email notification via SMTP/JavaMailSender. */
    EMAIL,

    /** Webhook notification via HTTP POST (WebClient). */
    WEBHOOK,

    /** In-application notification (stored/logged for in-app delivery). */
    IN_APP,

    /** SMS notification (extensible - no default channel provided). */
    SMS
}
