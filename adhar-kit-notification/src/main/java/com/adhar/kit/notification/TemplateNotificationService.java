package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing notification templates and sending template-based notifications.
 * <p>
 * Templates are stored in an in-memory {@link ConcurrentHashMap} and can be registered
 * at runtime. When sending from a template, {@code ${variable}} placeholders are substituted
 * into the template's subject and body, and the resulting notification is delegated to
 * {@link NotificationService}.
 * </p>
 * <p><b>Localization.</b> When a {@link MessageSource} is supplied and a {@link Locale} is
 * provided to {@link #sendFromTemplate(String, String, Map, Locale)}, the subject and body are
 * resolved from message codes {@code <templateId>.subject} and {@code <templateId>.body} using
 * the {@code MessageSource} (typically {@code ResourceBundle}-backed). If a code is not found the
 * template's own subject/body is used as a fallback.
 * </p>
 * <p><b>HTML templating.</b> A template whose {@code defaultMetadata} declares
 * {@code contentType=text/html} (or {@code html=true}) is rendered in HTML mode: variable values
 * substituted into the body are HTML-escaped to prevent markup injection, while the template
 * markup itself is preserved.
 * </p>
 *
 * @author Adhar Platform Team
 * @since 1.0.0
 */
@Slf4j
public class TemplateNotificationService {

    /** Metadata key indicating the rendered content type of a template. */
    public static final String CONTENT_TYPE_METADATA = "contentType";

    /** Metadata value marking a template as HTML content. */
    public static final String HTML_CONTENT_TYPE = "text/html";

    private final ConcurrentHashMap<String, NotificationTemplate> templates = new ConcurrentHashMap<>();
    private final NotificationService notificationService;
    private final MessageSource messageSource;

    /**
     * Creates a template service without localization support.
     *
     * @param notificationService the delegate service used to send rendered notifications
     */
    public TemplateNotificationService(NotificationService notificationService) {
        this(notificationService, null);
    }

    /**
     * Creates a template service with optional localization support.
     *
     * @param notificationService the delegate service used to send rendered notifications
     * @param messageSource       the message source for locale-aware subject/body resolution (may be {@code null})
     */
    public TemplateNotificationService(NotificationService notificationService, MessageSource messageSource) {
        this.notificationService = notificationService;
        this.messageSource = messageSource;
    }

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
     * Renders a registered template with the given variables and sends it to the specified recipient
     * using the default (non-localized) subject and body.
     *
     * @param templateId the id of the registered template
     * @param recipient  the notification recipient
     * @param variables  the variables for placeholder substitution
     * @throws IllegalArgumentException if no template is found with the given id
     */
    public void sendFromTemplate(String templateId, String recipient, Map<String, String> variables) {
        sendFromTemplate(templateId, recipient, variables, null);
    }

    /**
     * Renders a registered template with the given variables and locale, then sends it to the
     * specified recipient.
     * <p>
     * When a {@link MessageSource} is configured and {@code locale} is non-null, the subject and
     * body are resolved from message codes {@code <templateId>.subject}/{@code <templateId>.body},
     * falling back to the template's own values when a code is missing. HTML templates escape
     * substituted variable values in the body.
     * </p>
     *
     * @param templateId the id of the registered template
     * @param recipient  the notification recipient
     * @param variables  the variables for placeholder substitution (may be {@code null})
     * @param locale     the locale for message resolution, or {@code null} for the template defaults
     * @throws IllegalArgumentException if no template is found with the given id
     */
    public void sendFromTemplate(String templateId, String recipient, Map<String, String> variables, Locale locale) {
        var template = templates.get(templateId);
        if (template == null) {
            throw new IllegalArgumentException("No notification template found with id: " + templateId);
        }

        Map<String, String> vars = variables == null ? Map.of() : variables;
        boolean html = isHtml(template);

        String subjectTemplate = localize(templateId, "subject", template.subjectTemplate(), locale);
        String bodyTemplate = localize(templateId, "body", template.bodyTemplate(), locale);

        String subject = substitute(subjectTemplate, vars, false);
        String body = substitute(bodyTemplate, vars, html);

        Map<String, String> metadata = new HashMap<>(template.defaultMetadata());

        var notification = new Notification(
                UUID.randomUUID().toString(),
                template.type(),
                recipient,
                subject,
                body,
                Map.copyOf(metadata),
                null
        );

        log.debug("Sending notification from template [templateId={}, notificationId={}, recipient={}, locale={}, html={}]",
                templateId, notification.id(), recipient, locale, html);

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

    private static boolean isHtml(NotificationTemplate template) {
        Map<String, String> md = template.defaultMetadata();
        return HTML_CONTENT_TYPE.equalsIgnoreCase(md.get(CONTENT_TYPE_METADATA))
                || "true".equalsIgnoreCase(md.get("html"));
    }

    private String localize(String templateId, String part, String fallback, Locale locale) {
        if (messageSource != null && locale != null) {
            try {
                return messageSource.getMessage(templateId + "." + part, null, locale);
            } catch (NoSuchMessageException ex) {
                log.debug("No localized message for [{}.{}] locale={}; using template default",
                        templateId, part, locale);
            }
        }
        return fallback;
    }

    private static String substitute(String template, Map<String, String> variables, boolean html) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (var entry : variables.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("${" + entry.getKey() + "}", html ? escapeHtml(value) : value);
        }
        return result;
    }

    private static String escapeHtml(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
