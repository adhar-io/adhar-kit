package com.adhar.kit.notification.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationTemplate")
class NotificationTemplateTest {

    @Test
    @DisplayName("render replaces ${variable} placeholders")
    void renderReplacesVariablePlaceholders() {
        NotificationTemplate template = new NotificationTemplate(
                "welcome", "Welcome Email", NotificationType.EMAIL,
                "Welcome, ${name}!", "Hello ${name}, your account ${account} is ready.",
                null
        );

        NotificationTemplate.RenderedTemplate rendered = template.render(
                Map.of("name", "Alice", "account", "Premium")
        );

        assertThat(rendered.subject()).isEqualTo("Welcome, Alice!");
        assertThat(rendered.body()).isEqualTo("Hello Alice, your account Premium is ready.");
    }

    @Test
    @DisplayName("render with missing variables leaves placeholder intact")
    void renderWithMissingVariablesLeavesPlaceholder() {
        NotificationTemplate template = new NotificationTemplate(
                "alert", "Alert Template", NotificationType.IN_APP,
                "Alert for ${user}", "Issue: ${issue} in ${system}",
                null
        );

        NotificationTemplate.RenderedTemplate rendered = template.render(
                Map.of("user", "Bob")
        );

        assertThat(rendered.subject()).isEqualTo("Alert for Bob");
        assertThat(rendered.body()).isEqualTo("Issue: ${issue} in ${system}");
    }

    @Test
    @DisplayName("render with empty variables map leaves all placeholders")
    void renderWithEmptyVariablesMapLeavesAllPlaceholders() {
        NotificationTemplate template = new NotificationTemplate(
                "notify", "Notify", NotificationType.SMS,
                "Hi ${name}", "Code: ${code}",
                null
        );

        NotificationTemplate.RenderedTemplate rendered = template.render(Map.of());

        assertThat(rendered.subject()).isEqualTo("Hi ${name}");
        assertThat(rendered.body()).isEqualTo("Code: ${code}");
    }

    @Test
    @DisplayName("render with null subject template returns empty string")
    void renderWithNullSubjectTemplateReturnsEmptyString() {
        NotificationTemplate template = new NotificationTemplate(
                "webhook", "Webhook Notify", NotificationType.WEBHOOK,
                null, "Payload: ${data}",
                null
        );

        NotificationTemplate.RenderedTemplate rendered = template.render(
                Map.of("data", "test-payload")
        );

        assertThat(rendered.subject()).isEmpty();
        assertThat(rendered.body()).isEqualTo("Payload: test-payload");
    }

    @Test
    @DisplayName("default metadata is empty map when null provided")
    void defaultMetadataIsEmptyMapWhenNullProvided() {
        NotificationTemplate template = new NotificationTemplate(
                "test", "Test", NotificationType.EMAIL,
                "Subject", "Body",
                null
        );

        assertThat(template.defaultMetadata()).isNotNull();
        assertThat(template.defaultMetadata()).isEmpty();
    }

    @Test
    @DisplayName("render with multiple occurrences of same variable replaces all")
    void renderWithMultipleOccurrencesReplacesAll() {
        NotificationTemplate template = new NotificationTemplate(
                "repeat", "Repeat Template", NotificationType.EMAIL,
                "${name} notification", "Dear ${name}, this is for ${name}.",
                null
        );

        NotificationTemplate.RenderedTemplate rendered = template.render(
                Map.of("name", "Charlie")
        );

        assertThat(rendered.subject()).isEqualTo("Charlie notification");
        assertThat(rendered.body()).isEqualTo("Dear Charlie, this is for Charlie.");
    }
}
