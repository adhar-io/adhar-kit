package com.adhar.kit.notification;

import com.adhar.kit.commons.event.AdharCloudEvent;
import com.adhar.kit.notification.channel.InAppNotificationChannel;
import com.adhar.kit.notification.channel.NotificationChannel;
import com.adhar.kit.notification.model.Notification;
import com.adhar.kit.notification.model.NotificationEvent;
import com.adhar.kit.notification.model.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DefaultNotificationService")
class DefaultNotificationServiceTest {

    @Mock
    private InAppNotificationChannel channel;

    @Mock
    private NotificationRetryHandler retryHandler;

    @Mock
    private NotificationHistory history;

    private final Executor directExecutor = Runnable::run;

    private Notification notification;

    @BeforeEach
    void setUp() {
        when(channel.supports(NotificationType.IN_APP)).thenReturn(true);
        notification = new Notification("notif-1", NotificationType.IN_APP, "user-1",
                "Subject", "Body", null, null);
    }

    private DefaultNotificationService service(Consumer<AdharCloudEvent<?>> publisher) {
        return new DefaultNotificationService(List.of(channel), directExecutor, retryHandler, history, publisher);
    }

    @Test
    @DisplayName("send routes to supporting channel, records success history and publishes sent event")
    void sendSuccess() {
        @SuppressWarnings("unchecked")
        Consumer<AdharCloudEvent<?>> publisher = org.mockito.Mockito.mock(Consumer.class);
        DefaultNotificationService service = service(publisher);

        service.send(notification);

        verify(channel).send(notification);
        verify(history).record(eq(notification), eq(true), eq("IN_APP"), isNull());

        ArgumentCaptor<AdharCloudEvent<?>> captor = ArgumentCaptor.forClass(AdharCloudEvent.class);
        verify(publisher).accept(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("com.adhar.notification.sent");
        Object data = captor.getValue().data();
        assertThat(data).isInstanceOf(NotificationEvent.class);
        assertThat(((NotificationEvent) data).success()).isTrue();
    }

    @Test
    @DisplayName("send with no matching channel throws UnsupportedOperationException")
    void sendNoChannel() {
        DefaultNotificationService service =
                new DefaultNotificationService(List.of(), directExecutor, null, null);

        assertThatThrownBy(() -> service.send(notification))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("No notification channel found");
    }

    @Test
    @DisplayName("send failure with retry succeeding records success and publishes sent event")
    void sendFailureRetrySucceeds() {
        doThrow(new RuntimeException("boom")).when(channel).send(notification);
        when(retryHandler.retry(notification, channel)).thenReturn(true);
        @SuppressWarnings("unchecked")
        Consumer<AdharCloudEvent<?>> publisher = org.mockito.Mockito.mock(Consumer.class);
        DefaultNotificationService service = service(publisher);

        service.send(notification);

        verify(retryHandler).retry(notification, channel);
        verify(history).record(eq(notification), eq(true), eq("IN_APP"), isNull());
        ArgumentCaptor<AdharCloudEvent<?>> captor = ArgumentCaptor.forClass(AdharCloudEvent.class);
        verify(publisher).accept(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("com.adhar.notification.sent");
    }

    @Test
    @DisplayName("send failure with retry exhausted records failure, publishes failed event and rethrows")
    void sendFailureRetryExhausted() {
        doThrow(new RuntimeException("boom")).when(channel).send(notification);
        when(retryHandler.retry(notification, channel)).thenReturn(false);
        @SuppressWarnings("unchecked")
        Consumer<AdharCloudEvent<?>> publisher = org.mockito.Mockito.mock(Consumer.class);
        DefaultNotificationService service = service(publisher);

        assertThatThrownBy(() -> service.send(notification))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("after retries");

        verify(history).record(eq(notification), eq(false), eq("IN_APP"), anyString());
        ArgumentCaptor<AdharCloudEvent<?>> captor = ArgumentCaptor.forClass(AdharCloudEvent.class);
        verify(publisher).accept(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("com.adhar.notification.failed");
    }

    @Test
    @DisplayName("send failure without retry handler records failure, publishes failed event and rethrows original")
    void sendFailureNoRetryHandler() {
        doThrow(new IllegalStateException("nope")).when(channel).send(notification);
        @SuppressWarnings("unchecked")
        Consumer<AdharCloudEvent<?>> publisher = org.mockito.Mockito.mock(Consumer.class);
        DefaultNotificationService service =
                new DefaultNotificationService(List.of(channel), directExecutor, null, history, publisher);

        assertThatThrownBy(() -> service.send(notification))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("nope");

        verify(history).record(eq(notification), eq(false), eq("IN_APP"), eq("nope"));
        ArgumentCaptor<AdharCloudEvent<?>> captor = ArgumentCaptor.forClass(AdharCloudEvent.class);
        verify(publisher).accept(captor.capture());
        assertThat(captor.getValue().type()).isEqualTo("com.adhar.notification.failed");
    }

    @Test
    @DisplayName("send works with null history and null publisher (no-op branches)")
    void sendNullCollaborators() {
        DefaultNotificationService service =
                new DefaultNotificationService(List.of(channel), directExecutor, null, null, null);

        service.send(notification);

        verify(channel).send(notification);
        verifyNoInteractions(history);
    }

    @Test
    @DisplayName("publishCloudEvent swallows publisher exceptions")
    void publishExceptionSwallowed() {
        Consumer<AdharCloudEvent<?>> publisher = ce -> {
            throw new RuntimeException("publish failed");
        };
        DefaultNotificationService service = service(publisher);

        // Should not propagate the publisher exception
        service.send(notification);

        verify(channel).send(notification);
    }

    @Test
    @DisplayName("sendAsync delegates to send via executor")
    void sendAsync() {
        DefaultNotificationService service =
                new DefaultNotificationService(List.of(channel), directExecutor, null, null);

        service.sendAsync(notification).join();

        verify(channel).send(notification);
    }

    @Test
    @DisplayName("sendBatch continues when one notification fails")
    void sendBatchContinuesOnFailure() {
        Notification ok = new Notification("ok", NotificationType.IN_APP, "u", "s", "b", null, null);
        Notification bad = new Notification("bad", NotificationType.IN_APP, "u", "s", "b", null, null);
        doThrow(new RuntimeException("fail")).when(channel).send(bad);
        DefaultNotificationService service =
                new DefaultNotificationService(List.of(channel), directExecutor, null, null);

        service.sendBatch(List.of(bad, ok));

        verify(channel).send(bad);
        verify(channel).send(ok);
        verify(channel, times(2)).send(any(Notification.class));
    }

    @Test
    @DisplayName("two-arg constructor sets null event publisher and still sends")
    void twoArgConstructor() {
        DefaultNotificationService service =
                new DefaultNotificationService(List.of(channel), directExecutor, retryHandler, history);

        service.send(notification);

        verify(channel).send(notification);
        verify(history).record(eq(notification), eq(true), eq("IN_APP"), isNull());
    }

    private DefaultNotificationService serviceWithGuards(NotificationPreferenceStore prefs,
                                                         NotificationRateLimiter limiter,
                                                         NotificationIdempotencyStore idempotency) {
        return new DefaultNotificationService(List.of(channel), directExecutor, retryHandler, history,
                null, prefs, limiter, idempotency);
    }

    @Test
    @DisplayName("send is skipped silently when recipient opted out of the channel")
    void sendSkippedWhenOptedOut() {
        NotificationPreferenceStore prefs = org.mockito.Mockito.mock(NotificationPreferenceStore.class);
        when(prefs.isOptedOut("user-1", NotificationType.IN_APP)).thenReturn(true);
        DefaultNotificationService service = serviceWithGuards(prefs, null, null);

        service.send(notification);

        verify(channel, never()).send(any(Notification.class));
        verifyNoInteractions(history);
    }

    @Test
    @DisplayName("send proceeds when recipient has not opted out")
    void sendProceedsWhenNotOptedOut() {
        NotificationPreferenceStore prefs = org.mockito.Mockito.mock(NotificationPreferenceStore.class);
        when(prefs.isOptedOut("user-1", NotificationType.IN_APP)).thenReturn(false);
        DefaultNotificationService service = serviceWithGuards(prefs, null, null);

        service.send(notification);

        verify(channel).send(notification);
    }

    @Test
    @DisplayName("send is skipped and recorded as failure when rate-limited")
    void sendSkippedWhenRateLimited() {
        NotificationRateLimiter limiter = org.mockito.Mockito.mock(NotificationRateLimiter.class);
        when(limiter.tryAcquire("user-1", NotificationType.IN_APP)).thenReturn(false);
        DefaultNotificationService service = serviceWithGuards(null, limiter, null);

        service.send(notification);

        verify(channel, never()).send(any(Notification.class));
        verify(history).record(eq(notification), eq(false), eq("IN_APP"), eq("Rate limit exceeded"));
    }

    @Test
    @DisplayName("send proceeds when a rate-limit permit is acquired")
    void sendProceedsWhenPermitAcquired() {
        NotificationRateLimiter limiter = org.mockito.Mockito.mock(NotificationRateLimiter.class);
        when(limiter.tryAcquire("user-1", NotificationType.IN_APP)).thenReturn(true);
        DefaultNotificationService service = serviceWithGuards(null, limiter, null);

        service.send(notification);

        verify(channel).send(notification);
    }

    @Test
    @DisplayName("send is skipped silently for a duplicate idempotency key")
    void sendSkippedForDuplicateIdempotencyKey() {
        NotificationIdempotencyStore idempotency = org.mockito.Mockito.mock(NotificationIdempotencyStore.class);
        when(idempotency.register("key-1")).thenReturn(false);
        Notification withKey = new Notification("notif-2", NotificationType.IN_APP, "user-1",
                "S", "B", Map.of(DefaultNotificationService.IDEMPOTENCY_KEY_METADATA, "key-1"), null);
        DefaultNotificationService service = serviceWithGuards(null, null, idempotency);

        service.send(withKey);

        verify(channel, never()).send(any(Notification.class));
        verifyNoInteractions(history);
    }

    @Test
    @DisplayName("send proceeds for a new idempotency key and when no key is present")
    void sendProceedsForNewIdempotencyKey() {
        NotificationIdempotencyStore idempotency = org.mockito.Mockito.mock(NotificationIdempotencyStore.class);
        when(idempotency.register("key-1")).thenReturn(true);
        Notification withKey = new Notification("notif-2", NotificationType.IN_APP, "user-1",
                "S", "B", Map.of(DefaultNotificationService.IDEMPOTENCY_KEY_METADATA, "key-1"), null);
        DefaultNotificationService service = serviceWithGuards(null, null, idempotency);

        service.send(withKey);
        // notification without any idempotency key is unaffected
        service.send(notification);

        verify(channel).send(withKey);
        verify(channel).send(notification);
    }
}
