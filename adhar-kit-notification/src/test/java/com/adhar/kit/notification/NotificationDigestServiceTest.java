package com.adhar.kit.notification;

import com.adhar.kit.notification.config.NotificationProperties;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationDigestService")
class NotificationDigestServiceTest {

    @Mock
    private NotificationService notificationService;

    private NotificationProperties.DigestProperties props(long windowMs, int maxBatchSize) {
        NotificationProperties.DigestProperties p = new NotificationProperties.DigestProperties();
        p.setWindowMs(windowMs);
        p.setMaxBatchSize(maxBatchSize);
        p.setSubjectTemplate("You have ${count} new notifications");
        return p;
    }

    private Notification notification(String id, String recipient, String subject, String body) {
        return new Notification(id, NotificationType.IN_APP, recipient, subject, body, null, null);
    }

    @Test
    @DisplayName("reaching max batch size flushes immediately as an aggregated digest")
    void maxBatchSizeFlushesImmediately() {
        NotificationDigestService digest =
                new NotificationDigestService(notificationService, props(60000, 2));

        digest.submit(notification("1", "u1", "S1", "B1"));
        digest.submit(notification("2", "u1", "S2", "B2"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());
        Notification sent = captor.getValue();
        assertThat(sent.subject()).isEqualTo("You have 2 new notifications");
        assertThat(sent.metadata()).containsEntry(NotificationDigestService.DIGEST_METADATA_KEY, "true");
        assertThat(sent.metadata()).containsEntry(NotificationDigestService.DIGEST_COUNT_METADATA_KEY, "2");
        assertThat(sent.body()).contains("S1: B1").contains("S2: B2");
        assertThat(digest.pendingCount()).isZero();
        digest.close();
    }

    @Test
    @DisplayName("a single buffered notification is flushed as-is, not wrapped in a digest")
    void singleNotificationSentAsIs() {
        NotificationDigestService digest =
                new NotificationDigestService(notificationService, props(60000, 50));
        Notification single = notification("1", "u1", "S1", "B1");

        digest.submit(single);
        assertThat(digest.pendingCount()).isEqualTo(1);
        digest.flushAll();

        verify(notificationService).send(single);
    }

    @Test
    @DisplayName("flushAll aggregates multiple notifications per recipient into one digest")
    void flushAllAggregates() {
        NotificationDigestService digest =
                new NotificationDigestService(notificationService, props(60000, 50));

        digest.submit(notification("1", "u1", "S1", "B1"));
        digest.submit(notification("2", "u1", null, "B2"));
        digest.submit(notification("3", "u2", "S3", "B3"));
        assertThat(digest.pendingCount()).isEqualTo(3);

        digest.flushAll();

        // u1 -> one aggregated digest, u2 -> one single notification
        verify(notificationService, times(2)).send(any(Notification.class));
        assertThat(digest.pendingCount()).isZero();
    }

    @Test
    @DisplayName("scheduled window elapsing flushes the bucket")
    void windowFlush() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(inv -> {
            latch.countDown();
            return null;
        }).when(notificationService).send(any(Notification.class));

        NotificationDigestService digest =
                new NotificationDigestService(notificationService, props(100, 50));
        digest.submit(notification("1", "u1", "S1", "B1"));

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        verify(notificationService).send(any(Notification.class));
        digest.close();
    }

    @Test
    @DisplayName("send failures during flush are swallowed")
    void flushSwallowsFailures() {
        doAnswer(inv -> {
            throw new RuntimeException("boom");
        }).when(notificationService).send(any(Notification.class));
        NotificationDigestService digest =
                new NotificationDigestService(notificationService, props(60000, 50));
        digest.submit(notification("1", "u1", "S1", "B1"));

        digest.flushAll(); // must not propagate

        verify(notificationService).send(any(Notification.class));
    }

    @Test
    @DisplayName("close flushes pending buckets and shuts down the owned scheduler")
    void closeFlushes() {
        NotificationDigestService digest =
                new NotificationDigestService(notificationService, props(60000, 50));
        digest.submit(notification("1", "u1", "S1", "B1"));

        digest.close();

        verify(notificationService).send(any(Notification.class));
    }

    @Test
    @DisplayName("caller-supplied scheduler is not shut down on close")
    void externalSchedulerNotShutDown() {
        java.util.concurrent.ScheduledExecutorService scheduler =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        try {
            NotificationDigestService digest =
                    new NotificationDigestService(notificationService, props(60000, 50), scheduler);
            digest.submit(notification("1", "u1", "S1", "B1"));
            digest.close();

            verify(notificationService).send(any(Notification.class));
            assertThat(scheduler.isShutdown()).isFalse();
        } finally {
            scheduler.shutdownNow();
        }
    }

    @Test
    @DisplayName("submitting null throws NullPointerException")
    void submitNull() {
        NotificationDigestService digest =
                new NotificationDigestService(notificationService, props(60000, 50));
        try {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> digest.submit(null))
                    .isInstanceOf(NullPointerException.class);
        } finally {
            digest.close();
        }
        verify(notificationService, never()).send(any(Notification.class));
    }

    @Test
    @DisplayName("digest body lists bodies for notifications lacking a subject")
    void bodyWithoutSubject() {
        NotificationDigestService digest =
                new NotificationDigestService(notificationService, props(60000, 2));
        digest.submit(notification("1", "u1", "  ", "B1"));
        digest.submit(notification("2", "u1", null, "B2"));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationService).send(captor.capture());
        List<String> lines = captor.getValue().body().lines().toList();
        assertThat(lines).containsExactly("- B1", "- B2");
        digest.close();
    }
}
