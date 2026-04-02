package com.adhar.kit.notification.model;

import java.util.Map;

/**
 * Immutable template record for rendering notifications with variable substitution.
 * <p>
 * Templates support simple {@code ${variable}} replacement in both subject and body fields.
 * Default metadata values can be specified and will be included unless overridden by
 * variables at render time.
 * </p>
 *
 * @param id              unique identifier for the template
 * @param name            human-readable template name
 * @param type            the notification type this template targets
 * @param subjectTemplate the subject template with {@code ${variable}} placeholders
 * @param bodyTemplate    the body template with {@code ${variable}} placeholders
 * @param defaultMetadata default metadata key-value pairs included with rendered notifications
 * @author Adhar Platform Team
 * @since 1.0.0
 */
public record NotificationTemplate(
        String id,
        String name,
        NotificationType type,
        String subjectTemplate,
        String bodyTemplate,
        Map<String, String> defaultMetadata
) {

    /**
     * Compact constructor that provides defaults for null fields.
     */
    public NotificationTemplate {
        if (defaultMetadata == null) {
            defaultMetadata = Map.of();
        }
    }

    /**
     * Renders this template by replacing {@code ${variable}} placeholders in the subject
     * and body with the provided variable values.
     *
     * @param variables the variable name-value pairs for substitution
     * @return a rendered {@link RenderedTemplate} containing the resolved subject and body
     */
    public RenderedTemplate render(Map<String, String> variables) {
        String renderedSubject = replacePlaceholders(subjectTemplate, variables);
        String renderedBody = replacePlaceholders(bodyTemplate, variables);
        return new RenderedTemplate(renderedSubject, renderedBody);
    }

    private String replacePlaceholders(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        for (var entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * Result of rendering a notification template.
     *
     * @param subject the rendered subject
     * @param body    the rendered body
     */
    public record RenderedTemplate(String subject, String body) {
    }
}
