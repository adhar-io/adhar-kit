package com.adhar.kit.notification;

import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationHistoryEntry;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("NotificationHistory")
class NotificationHistoryTest {

    private NotificationHistory history;

    @BeforeEach
    void setUp() {
        history = new NotificationHistory(1000);
    }

    private Notification createNotification(String id) {
        return new Notification(id, NotificationType.EMAIL, "user@test.com",
                "Subject", "Body", null, null);
    }

    @Test
    @DisplayName("record adds entry to history")
    void recordAddsEntry() {
        Notification notification = createNotification("notif-1");

        history.record(notification, true, "EMAIL");

        assertThat(history.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("getHistory returns last N entries")
    void getHistoryReturnsLastNEntries() {
        for (int i = 0; i < 10; i++) {
            history.record(createNotification("notif-" + i), true, "EMAIL");
        }

        List<NotificationHistoryEntry> result = history.getHistory(5);

        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("getHistory returns all entries when limit exceeds size")
    void getHistoryReturnsAllEntriesWhenLimitExceedsSize() {
        history.record(createNotification("notif-1"), true, "EMAIL");
        history.record(createNotification("notif-2"), true, "EMAIL");

        List<NotificationHistoryEntry> result = history.getHistory(100);

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getFailedNotifications returns only failures")
    void getFailedNotificationsReturnsOnlyFailures() {
        history.record(createNotification("notif-1"), true, "EMAIL");
        history.record(createNotification("notif-2"), false, "EMAIL", "connection timeout");
        history.record(createNotification("notif-3"), true, "SMS");
        history.record(createNotification("notif-4"), false, "WEBHOOK", "server error");

        List<NotificationHistoryEntry> failures = history.getFailedNotifications();

        assertThat(failures).hasSize(2);
        assertThat(failures).allMatch(entry -> !entry.success());
    }

    @Test
    @DisplayName("bounded size does not exceed max 1000 entries")
    void boundedSizeDoesNotExceedMax() {
        NotificationHistory boundedHistory = new NotificationHistory(1000);

        for (int i = 0; i < 1050; i++) {
            boundedHistory.record(createNotification("notif-" + i), true, "EMAIL");
        }

        assertThat(boundedHistory.size()).isEqualTo(1000);
    }

    @Test
    @DisplayName("bounded size with smaller limit")
    void boundedSizeWithSmallerLimit() {
        NotificationHistory smallHistory = new NotificationHistory(5);

        for (int i = 0; i < 10; i++) {
            smallHistory.record(createNotification("notif-" + i), true, "EMAIL");
        }

        assertThat(smallHistory.size()).isEqualTo(5);
    }

    @Test
    @DisplayName("clear empties history")
    void clearEmptiesHistory() {
        history.record(createNotification("notif-1"), true, "EMAIL");
        history.record(createNotification("notif-2"), false, "WEBHOOK", "error");

        assertThat(history.size()).isEqualTo(2);

        history.clear();

        assertThat(history.size()).isZero();
        assertThat(history.getHistory(100)).isEmpty();
        assertThat(history.getFailedNotifications()).isEmpty();
    }
}
