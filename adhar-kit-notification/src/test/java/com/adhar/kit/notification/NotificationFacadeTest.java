package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationHistoryEntry;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationFacade")
class NotificationFacadeTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private TemplateNotificationService templateNotificationService;

    @Mock
    private NotificationHistory notificationHistory;

    private NotificationFacade facade;

    @BeforeEach
    void setUp() {
        facade = new NotificationFacade(notificationService, templateNotificationService, notificationHistory);
    }

    @Test
    @DisplayName("sendEmail creates EMAIL notification and delegates to service")
    void sendEmailCreatesEmailNotificationAndDelegatesToService() {
        facade.sendEmail("user@example.com", "Test Subject", "Test Body");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());

        Notification sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(NotificationType.EMAIL);
        assertThat(sent.recipient()).isEqualTo("user@example.com");
        assertThat(sent.subject()).isEqualTo("Test Subject");
        assertThat(sent.body()).isEqualTo("Test Body");
        assertThat(sent.id()).isNotNull();
    }

    @Test
    @DisplayName("sendWebhook creates WEBHOOK notification")
    void sendWebhookCreatesWebhookNotification() {
        facade.sendWebhook("https://hooks.example.com/notify", "{\"event\":\"test\"}");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());

        Notification sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(NotificationType.WEBHOOK);
        assertThat(sent.recipient()).isEqualTo("https://hooks.example.com/notify");
        assertThat(sent.body()).isEqualTo("{\"event\":\"test\"}");
        assertThat(sent.subject()).isNull();
    }

    @Test
    @DisplayName("sendInApp creates IN_APP notification")
    void sendInAppCreatesInAppNotification() {
        facade.sendInApp("user-42", "You have a new message");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());

        Notification sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(NotificationType.IN_APP);
        assertThat(sent.recipient()).isEqualTo("user-42");
        assertThat(sent.body()).isEqualTo("You have a new message");
    }

    @Test
    @DisplayName("sendSms creates SMS notification")
    void sendSmsCreatesSmsNotification() {
        facade.sendSms("+1234567890", "Your code is 1234");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());

        Notification sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(NotificationType.SMS);
        assertThat(sent.recipient()).isEqualTo("+1234567890");
        assertThat(sent.body()).isEqualTo("Your code is 1234");
    }

    @Test
    @DisplayName("sendAsync returns CompletableFuture")
    void sendAsyncReturnsCompletableFuture() {
        Notification notification = new Notification(
                "notif-1", NotificationType.EMAIL, "user@test.com",
                "Subject", "Body", null, null
        );
        CompletableFuture<Void> expected = CompletableFuture.completedFuture(null);
        when(notificationService.sendAsync(any(Notification.class))).thenReturn(expected);

        CompletableFuture<Void> result = facade.sendAsync(notification);

        assertThat(result).isSameAs(expected);
        verify(notificationService).sendAsync(notification);
    }

    @Test
    @DisplayName("sendFromTemplate delegates to TemplateNotificationService")
    void sendFromTemplateDelegatesToTemplateNotificationService() {
        Map<String, String> variables = Map.of("name", "Alice");

        facade.sendFromTemplate("welcome-template", "alice@example.com", variables);

        verify(templateNotificationService).sendFromTemplate("welcome-template", "alice@example.com", variables);
    }

    @Test
    @DisplayName("getHistory returns entries")
    void getHistoryReturnsEntries() {
        Notification notification = new Notification(
                "notif-1", NotificationType.EMAIL, "user@test.com",
                "Subject", "Body", null, null
        );
        List<NotificationHistoryEntry> expected = List.of(
                new NotificationHistoryEntry("entry-1", notification, true, "EMAIL", Instant.now(), null)
        );
        when(notificationHistory.getHistory(10)).thenReturn(expected);

        List<NotificationHistoryEntry> result = facade.getHistory(10);

        assertThat(result).isEqualTo(expected);
        verify(notificationHistory).getHistory(10);
    }

    @Test
    @DisplayName("getFailedNotifications returns only failures")
    void getFailedNotificationsReturnsOnlyFailures() {
        Notification notification = new Notification(
                "notif-2", NotificationType.WEBHOOK, "https://hook.test",
                null, "{}", null, null
        );
        List<NotificationHistoryEntry> expected = List.of(
                new NotificationHistoryEntry("entry-2", notification, false, "WEBHOOK", Instant.now(), "timeout")
        );
        when(notificationHistory.getFailedNotifications()).thenReturn(expected);

        List<NotificationHistoryEntry> result = facade.getFailedNotifications();

        assertThat(result).isEqualTo(expected);
        assertThat(result).allMatch(entry -> !entry.success());
    }

    @Test
    @DisplayName("isEnabled returns true when service configured")
    void isEnabledReturnsTrueWhenServiceConfigured() {
        assertThat(facade.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("isEnabled returns false when service is null")
    void isEnabledReturnsFalseWhenServiceIsNull() {
        NotificationFacade disabledFacade = new NotificationFacade(null, null, null);
        assertThat(disabledFacade.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("health returns expected map")
    void healthReturnsExpectedMap() {
        when(notificationHistory.size()).thenReturn(5);
        when(notificationHistory.getFailedNotifications()).thenReturn(List.of());
        when(templateNotificationService.templateCount()).thenReturn(3);

        Map<String, Object> health = facade.health();

        assertThat(health).containsEntry("enabled", true);
        assertThat(health).containsEntry("notificationService", "available");
        assertThat(health).containsEntry("templateService", "available");
        assertThat(health).containsEntry("history", "available");
        assertThat(health).containsEntry("historySize", 5);
        assertThat(health).containsEntry("failedCount", 0);
        assertThat(health).containsEntry("templateCount", 3);
    }
}
