package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationTemplate;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.context.support.StaticMessageSource;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("TemplateNotificationService")
class TemplateNotificationServiceTest {

    @Mock
    private NotificationService notificationService;

    private TemplateNotificationService service;

    @BeforeEach
    void setUp() {
        service = new TemplateNotificationService(notificationService);
    }

    private NotificationTemplate welcomeTemplate() {
        return new NotificationTemplate(
                "welcome",
                "Welcome Email",
                NotificationType.EMAIL,
                "Hi ${name}",
                "Welcome ${name} to ${product}",
                Map.of("category", "onboarding"));
    }

    @Test
    @DisplayName("registerTemplate stores template and templateCount reflects it")
    void registerTemplate() {
        assertThat(service.templateCount()).isZero();

        service.registerTemplate(welcomeTemplate());

        assertThat(service.templateCount()).isEqualTo(1);
        assertThat(service.getTemplate("welcome")).isNotNull();
    }

    @Test
    @DisplayName("registerTemplate rejects null id")
    void registerTemplateNullId() {
        NotificationTemplate t = new NotificationTemplate(
                null, "n", NotificationType.EMAIL, "s", "b", null);

        assertThatThrownBy(() -> service.registerTemplate(t))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");
    }

    @Test
    @DisplayName("registerTemplate rejects blank id")
    void registerTemplateBlankId() {
        NotificationTemplate t = new NotificationTemplate(
                "   ", "n", NotificationType.EMAIL, "s", "b", null);

        assertThatThrownBy(() -> service.registerTemplate(t))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("sendFromTemplate renders placeholders, merges metadata and delegates to service")
    void sendFromTemplate() {
        service.registerTemplate(welcomeTemplate());

        service.sendFromTemplate("welcome", "alice@example.com",
                Map.of("name", "Alice", "product", "Adhar"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());
        Notification sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(NotificationType.EMAIL);
        assertThat(sent.recipient()).isEqualTo("alice@example.com");
        assertThat(sent.subject()).isEqualTo("Hi Alice");
        assertThat(sent.body()).isEqualTo("Welcome Alice to Adhar");
        assertThat(sent.metadata()).containsEntry("category", "onboarding");
    }

    @Test
    @DisplayName("sendFromTemplate throws when template not found")
    void sendFromTemplateNotFound() {
        assertThatThrownBy(() -> service.sendFromTemplate("missing", "x", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No notification template found");
    }

    @Test
    @DisplayName("getTemplate returns null when absent")
    void getTemplateAbsent() {
        assertThat(service.getTemplate("none")).isNull();
    }

    @Test
    @DisplayName("removeTemplate removes and returns the template")
    void removeTemplate() {
        service.registerTemplate(welcomeTemplate());

        NotificationTemplate removed = service.removeTemplate("welcome");

        assertThat(removed).isNotNull();
        assertThat(service.templateCount()).isZero();
        assertThat(service.removeTemplate("welcome")).isNull();
    }

    @Test
    @DisplayName("sendFromTemplate with locale resolves localized subject/body from MessageSource")
    void sendFromTemplateLocalized() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("welcome.subject", Locale.FRENCH, "Bonjour ${name}");
        messages.addMessage("welcome.body", Locale.FRENCH, "Bienvenue ${name} sur ${product}");
        TemplateNotificationService localizedService =
                new TemplateNotificationService(notificationService, messages);
        localizedService.registerTemplate(welcomeTemplate());

        localizedService.sendFromTemplate("welcome", "alice@example.com",
                Map.of("name", "Alice", "product", "Adhar"), Locale.FRENCH);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());
        Notification sent = captor.getValue();
        assertThat(sent.subject()).isEqualTo("Bonjour Alice");
        assertThat(sent.body()).isEqualTo("Bienvenue Alice sur Adhar");
    }

    @Test
    @DisplayName("sendFromTemplate falls back to template defaults when message code missing")
    void sendFromTemplateLocalizedFallback() {
        StaticMessageSource messages = new StaticMessageSource();
        TemplateNotificationService localizedService =
                new TemplateNotificationService(notificationService, messages);
        localizedService.registerTemplate(welcomeTemplate());

        localizedService.sendFromTemplate("welcome", "alice@example.com",
                Map.of("name", "Alice", "product", "Adhar"), Locale.GERMAN);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());
        assertThat(captor.getValue().subject()).isEqualTo("Hi Alice");
        assertThat(captor.getValue().body()).isEqualTo("Welcome Alice to Adhar");
    }

    @Test
    @DisplayName("HTML template escapes substituted variable values in the body")
    void sendFromTemplateHtmlEscaping() {
        NotificationTemplate htmlTemplate = new NotificationTemplate(
                "alert",
                "Alert",
                NotificationType.EMAIL,
                "Alert for ${name}",
                "<p>Hello ${name}</p>",
                Map.of(TemplateNotificationService.CONTENT_TYPE_METADATA,
                        TemplateNotificationService.HTML_CONTENT_TYPE));
        service.registerTemplate(htmlTemplate);

        service.sendFromTemplate("alert", "bob@example.com",
                Map.of("name", "<b>x</b>&\"'"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());
        Notification sent = captor.getValue();
        assertThat(sent.body()).isEqualTo("<p>Hello &lt;b&gt;x&lt;/b&gt;&amp;&quot;&#39;</p>");
        // subject is plain text, not escaped
        assertThat(sent.subject()).isEqualTo("Alert for <b>x</b>&\"'");
        assertThat(sent.metadata()).containsEntry(
                TemplateNotificationService.CONTENT_TYPE_METADATA, TemplateNotificationService.HTML_CONTENT_TYPE);
    }

    @Test
    @DisplayName("sendFromTemplate tolerates null variables map")
    void sendFromTemplateNullVariables() {
        service.registerTemplate(welcomeTemplate());

        service.sendFromTemplate("welcome", "alice@example.com", null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());
        // placeholders remain unresolved but no exception is thrown
        assertThat(captor.getValue().subject()).isEqualTo("Hi ${name}");
    }
}
