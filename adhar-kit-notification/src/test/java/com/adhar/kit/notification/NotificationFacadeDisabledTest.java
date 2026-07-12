package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationTemplate;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationFacade disabled and delegation paths")
class NotificationFacadeDisabledTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private TemplateNotificationService templateNotificationService;

    private final NotificationFacade disabled = new NotificationFacade(null, null, null);

    @Test
    @DisplayName("send throws when no NotificationService configured")
    void sendDisabled() {
        Notification n = new Notification("1", NotificationType.EMAIL, "x", "s", "b", null, null);
        assertThatThrownBy(() -> disabled.send(n))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NotificationService is not configured");
    }

    @Test
    @DisplayName("sendAsync throws when no NotificationService configured")
    void sendAsyncDisabled() {
        Notification n = new Notification("1", NotificationType.EMAIL, "x", "s", "b", null, null);
        assertThatThrownBy(() -> disabled.sendAsync(n))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("sendFromTemplate throws when no TemplateNotificationService configured")
    void sendFromTemplateDisabled() {
        assertThatThrownBy(() -> disabled.sendFromTemplate("t", "r", Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TemplateNotificationService is not configured");
    }

    @Test
    @DisplayName("registerTemplate throws when no TemplateNotificationService configured")
    void registerTemplateDisabled() {
        NotificationTemplate t = new NotificationTemplate("t", "n", NotificationType.EMAIL, "s", "b", null);
        assertThatThrownBy(() -> disabled.registerTemplate(t))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getHistory throws when no NotificationHistory configured")
    void getHistoryDisabled() {
        assertThatThrownBy(() -> disabled.getHistory(5))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NotificationHistory is not configured");
    }

    @Test
    @DisplayName("getFailedNotifications throws when no NotificationHistory configured")
    void getFailedDisabled() {
        assertThatThrownBy(disabled::getFailedNotifications)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("health reports everything unavailable when disabled")
    void healthDisabled() {
        Map<String, Object> health = disabled.health();
        assertThat(health).containsEntry("enabled", false);
        assertThat(health).containsEntry("notificationService", "unavailable");
        assertThat(health).containsEntry("templateService", "unavailable");
        assertThat(health).containsEntry("history", "unavailable");
        assertThat(health).doesNotContainKey("historySize");
        assertThat(health).doesNotContainKey("templateCount");
    }

    @Test
    @DisplayName("send delegates to the configured NotificationService")
    void sendDelegates() {
        NotificationFacade facade = new NotificationFacade(notificationService, null, null);
        Notification n = new Notification("1", NotificationType.EMAIL, "x", "s", "b", null, null);

        facade.send(n);

        verify(notificationService).send(n);
    }

    @Test
    @DisplayName("registerTemplate delegates to the configured TemplateNotificationService")
    void registerTemplateDelegates() {
        NotificationFacade facade = new NotificationFacade(null, templateNotificationService, null);
        NotificationTemplate t = new NotificationTemplate("t", "n", NotificationType.EMAIL, "s", "b", null);

        facade.registerTemplate(t);

        verify(templateNotificationService).registerTemplate(t);
    }

    @Test
    @DisplayName("getInstance returns a non-null singleton")
    void getInstance() {
        NotificationFacade a = NotificationFacade.getInstance();
        NotificationFacade b = NotificationFacade.getInstance();
        assertThat(a).isNotNull().isSameAs(b);
    }
}
