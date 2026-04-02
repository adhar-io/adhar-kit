package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing notification templates and sending template-based notifications.
 * <p>
 * Templates are stored in an in-memory {@link ConcurrentHashMap} and can be registered
 * at runtime. When sending from a template, variables are substituted into the template's
 * subject and body, and the resulting notification is delegated to {@link NotificationService}.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class TemplateNotificationService {

    private final ConcurrentHashMap<String, NotificationTemplate> templates = new ConcurrentHashMap<>();
    private final NotificationService notificationService;

    /**
     * Registers a notification template for later use.
     *
     * @param template the template to register
     * @throws IllegalArgumentException if the template id is null or blank
     */
    public void registerTemplate(NotificationTemplate template) {
        if (template.id() == null || template.id().isBlank()) {
            throw new IllegalArgumentException("Template id must not be null or blank");
        }
        templates.put(template.id(), template);
        log.info("Registered notification template [id={}, name={}, type={}]",
                template.id(), template.name(), template.type());
    }

    /**
     * Renders a registered template with the given variables and sends it to the specified recipient.
     * <p>
     * The template's default metadata is merged with any metadata derived from the variables,
     * with the template defaults taking lower precedence.
     * </p>
     *
     * @param templateId the id of the registered template
     * @param recipient  the notification recipient
     * @param variables  the variables for placeholder substitution
     * @throws IllegalArgumentException if no template is found with the given id
     */
    public void sendFromTemplate(String templateId, String recipient, Map<String, String> variables) {
        var template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("No notification template found with id: " + templateId);
        }

        var rendered = template.render(variables);

        // Merge default metadata from the template
        Map<String, String> metadata = new HashMap<>(template.defaultMetadata());

        var notification = new Notification(
                UUID.randomUUID().toString(),
                template.type(),
                recipient,
                rendered.subject(),
                rendered.body(),
                Map.copyOf(metadata),
                null
        );

        log.debug("Sending notification from template [templateId={}, notificationId={}, recipient={}]",
                templateId, notification.id(), recipient);

        notificationService.send(notification);
    }

    /**
     * Returns the currently registered template for the given id, or {@code null} if not found.
     *
     * @param templateId the template id
     * @return the template, or {@code null}
     */
    public NotificationTemplate getTemplate(String templateId) {
        return templates.get(templateId);
    }

    /**
     * Removes a registered template by id.
     *
     * @param templateId the template id to remove
     * @return the removed template, or {@code null} if not found
     */
    public NotificationTemplate removeTemplate(String templateId) {
        return templates.remove(templateId);
    }

    /**
     * Returns the number of registered templates.
     *
     * @return the template count
     */
    public int templateCount() {
        return templates.size();
    }
}
