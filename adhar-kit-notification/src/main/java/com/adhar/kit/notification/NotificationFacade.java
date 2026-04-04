package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationHistoryEntry;
import com.adhar.kit.notification.model.NotificationTemplate;
import com.adhar.kit.notification.model.NotificationType;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified facade providing simplified access to the Adhar notification subsystem.
 * <p>
 * Wraps {@link NotificationService}, {@link TemplateNotificationService}, and
 * {@link NotificationHistory} behind a single entry point with convenience methods
 * for common notification patterns (email, SMS, webhook, in-app).
 * </p>
 * <p>
 * Supports a static singleton via {@link #getInstance()} for framework-free usage
 * (creates a default disabled instance if not yet initialized),
 * as well as direct construction for dependency-injected environments.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class NotificationFacade {

    private static volatile NotificationFacade instance;

    private final NotificationService notificationService;
    private final TemplateNotificationService templateNotificationService;
    private final NotificationHistory notificationHistory;

    /**
     * Constructs a new NotificationFacade with all optional collaborators.
     *
     * @param notificationService         the notification service for sending, or {@code null}
     * @param templateNotificationService the template service for template-based sends, or {@code null}
     * @param notificationHistory         the notification history tracker, or {@code null}
     */
    public NotificationFacade(NotificationService notificationService,
                              TemplateNotificationService templateNotificationService,
                              NotificationHistory notificationHistory) {
        this.notificationService = notificationService;
        this.templateNotificationService = templateNotificationService;
        this.notificationHistory = notificationHistory;
        log.info("NotificationFacade initialized [notificationService={}, templateService={}, history={}]",
                notificationService != null, templateNotificationService != null, notificationHistory != null);
    }

    /**
     * Returns the singleton instance of the NotificationFacade.
     * <p>
     * Uses volatile double-checked locking for thread safety. If no instance
     * has been created yet, a default disabled instance (all collaborators
     * {@code null}) is constructed automatically.
     * </p>
     *
     * @return the singleton instance (never {@code null})
     */
    public static NotificationFacade getInstance() {
        if (instance == null) {
            synchronized (NotificationFacade.class) {
                if (instance == null) {
                    instance = new NotificationFacade(null, null, null);
                }
            }
        }
        return instance;
    }

    /**
     * Sends a notification synchronously through the configured notification service.
     *
     * @param notification the notification to send
     * @throws IllegalStateException if no NotificationService is configured
     */
    public void send(Notification notification) {
        requireNotificationService();
        log.debug("Sending notification [id={}, type={}, recipient={}]",
                notification.id(), notification.type(), notification.recipient());
        notificationService.send(notification);
    }

    /**
     * Convenience method to send an email notification.
     *
     * @param recipient the email address of the recipient
     * @param subject   the email subject
     * @param body      the email body
     */
    public void sendEmail(String recipient, String subject, String body) {
        var notification = new Notification(
                UUID.randomUUID().toString(),
                NotificationType.EMAIL,
                recipient,
                subject,
                body,
                null,
                null
        );
        send(notification);
    }

    /**
     * Convenience method to send a webhook notification.
     *
     * @param url     the webhook URL
     * @param payload the payload to deliver
     */
    public void sendWebhook(String url, String payload) {
        var notification = new Notification(
                UUID.randomUUID().toString(),
                NotificationType.WEBHOOK,
                url,
                null,
                payload,
                null,
                null
        );
        send(notification);
    }

    /**
     * Convenience method to send an in-app notification.
     *
     * @param userId  the target user identifier
     * @param message the notification message
     */
    public void sendInApp(String userId, String message) {
        var notification = new Notification(
                UUID.randomUUID().toString(),
                NotificationType.IN_APP,
                userId,
                null,
                message,
                null,
                null
        );
        send(notification);
    }

    /**
     * Convenience method to send an SMS notification.
     *
     * @param phoneNumber the recipient phone number
     * @param message     the SMS message
     */
    public void sendSms(String phoneNumber, String message) {
        var notification = new Notification(
                UUID.randomUUID().toString(),
                NotificationType.SMS,
                phoneNumber,
                null,
                message,
                null,
                null
        );
        send(notification);
    }

    /**
     * Sends a notification asynchronously through the configured notification service.
     *
     * @param notification the notification to send
     * @return a CompletableFuture that completes when the notification is sent
     * @throws IllegalStateException if no NotificationService is configured
     */
    public CompletableFuture<Void> sendAsync(Notification notification) {
        requireNotificationService();
        log.debug("Sending notification asynchronously [id={}, type={}, recipient={}]",
                notification.id(), notification.type(), notification.recipient());
        return notificationService.sendAsync(notification);
    }

    /**
     * Sends a notification rendered from a registered template with variable substitution.
     *
     * @param templateId the id of the registered template
     * @param recipient  the notification recipient
     * @param variables  the variables for placeholder substitution
     * @throws IllegalStateException    if no TemplateNotificationService is configured
     * @throws IllegalArgumentException if no template is found with the given id
     */
    public void sendFromTemplate(String templateId, String recipient, Map<String, String> variables) {
        requireTemplateNotificationService();
        log.debug("Sending notification from template [templateId={}, recipient={}]", templateId, recipient);
        templateNotificationService.sendFromTemplate(templateId, recipient, variables);
    }

    /**
     * Registers a notification template for later use with {@link #sendFromTemplate}.
     *
     * @param template the template to register
     * @throws IllegalStateException    if no TemplateNotificationService is configured
     * @throws IllegalArgumentException if the template id is null or blank
     */
    public void registerTemplate(NotificationTemplate template) {
        requireTemplateNotificationService();
        templateNotificationService.registerTemplate(template);
    }

    /**
     * Returns the most recent notification history entries, up to the specified limit.
     *
     * @param limit the maximum number of entries to return
     * @return an unmodifiable list of the most recent history entries
     * @throws IllegalStateException if no NotificationHistory is configured
     */
    public List<NotificationHistoryEntry> getHistory(int limit) {
        requireNotificationHistory();
        return notificationHistory.getHistory(limit);
    }

    /**
     * Returns all failed notification history entries.
     *
     * @return an unmodifiable list of failed notification entries
     * @throws IllegalStateException if no NotificationHistory is configured
     */
    public List<NotificationHistoryEntry> getFailedNotifications() {
        requireNotificationHistory();
        return notificationHistory.getFailedNotifications();
    }

    /**
     * Returns whether the notification facade is enabled, meaning at least a
     * {@link NotificationService} is configured.
     *
     * @return {@code true} if the notification service is available
     */
    public boolean isEnabled() {
        return notificationService != null;
    }

    /**
     * Returns a health status map describing the state of the notification subsystem.
     *
     * @return a map containing health information
     */
    public Map<String, Object> health() {
        Map<String, Object> healthMap = new LinkedHashMap<>();
        healthMap.put("enabled", isEnabled());
        healthMap.put("notificationService", notificationService != null ? "available" : "unavailable");
        healthMap.put("templateService", templateNotificationService != null ? "available" : "unavailable");
        healthMap.put("history", notificationHistory != null ? "available" : "unavailable");
        if (notificationHistory != null) {
            healthMap.put("historySize", notificationHistory.size());
            healthMap.put("failedCount", notificationHistory.getFailedNotifications().size());
        }
        if (templateNotificationService != null) {
            healthMap.put("templateCount", templateNotificationService.templateCount());
        }
        return healthMap;
    }

    private void requireNotificationService() {
        if (notificationService == null) {
            throw new IllegalStateException("NotificationService is not configured");
        }
    }

    private void requireTemplateNotificationService() {
        if (templateNotificationService == null) {
            throw new IllegalStateException("TemplateNotificationService is not configured");
        }
    }

    private void requireNotificationHistory() {
        if (notificationHistory == null) {
            throw new IllegalStateException("NotificationHistory is not configured");
        }
    }
}
