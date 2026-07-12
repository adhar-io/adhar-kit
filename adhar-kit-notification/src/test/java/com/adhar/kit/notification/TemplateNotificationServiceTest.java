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
}
