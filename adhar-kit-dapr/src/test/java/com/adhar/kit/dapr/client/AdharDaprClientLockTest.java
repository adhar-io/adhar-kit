package com.adhar.kit.dapr.client;

import io.dapr.client.DaprClient;
import io.dapr.client.DaprPreviewClient;
import io.dapr.client.domain.LockRequest;
import io.dapr.client.domain.UnlockRequest;
import io.dapr.client.domain.UnlockResponseStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdharDaprClient}'s distributed-lock methods, backed by a mocked
 * {@link DaprPreviewClient}.
 */
class AdharDaprClientLockTest {

    private DaprPreviewClient previewClient;
    private AdharDaprClient dapr;

    @BeforeEach
    void setUp() {
        previewClient = mock(DaprPreviewClient.class);
        dapr = new AdharDaprClient(mock(DaprClient.class), previewClient);
    }

    @Test
    void tryLockReturnsTrueWhenAcquired() {
        when(previewClient.tryLock(any(LockRequest.class))).thenReturn(Mono.just(true));

        assertThat(dapr.tryLock("lockstore", "res", "owner", 30)).isTrue();
    }

    @Test
    void tryLockReturnsFalseWhenNullResult() {
        when(previewClient.tryLock(any(LockRequest.class))).thenReturn(Mono.empty());

        assertThat(dapr.tryLock("lockstore", "res", "owner", 30)).isFalse();
    }

    @Test
    void tryLockWrapsException() {
        when(previewClient.tryLock(any(LockRequest.class))).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.tryLock("lockstore", "res", "owner", 30))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to acquire lock");
    }

    @Test
    void unlockReturnsTrueOnSuccess() {
        when(previewClient.unlock(any(UnlockRequest.class)))
            .thenReturn(Mono.just(UnlockResponseStatus.SUCCESS));

        assertThat(dapr.unlock("lockstore", "res", "owner")).isTrue();
    }

    @Test
    void unlockReturnsFalseWhenNotExist() {
        when(previewClient.unlock(any(UnlockRequest.class)))
            .thenReturn(Mono.just(UnlockResponseStatus.LOCK_UNEXIST));

        assertThat(dapr.unlock("lockstore", "res", "owner")).isFalse();
    }

    @Test
    void unlockWrapsException() {
        when(previewClient.unlock(any(UnlockRequest.class))).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.unlock("lockstore", "res", "owner"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to release lock");
    }

    @Test
    void tryLockWithoutPreviewClientThrowsIllegalState() {
        AdharDaprClient noPreview = new AdharDaprClient(mock(DaprClient.class));

        assertThatThrownBy(() -> noPreview.tryLock("lockstore", "res", "owner", 30))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unlockWithoutPreviewClientThrowsIllegalState() {
        AdharDaprClient noPreview = new AdharDaprClient(mock(DaprClient.class));

        assertThatThrownBy(() -> noPreview.unlock("lockstore", "res", "owner"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeClosesBothClients() throws Exception {
        DaprClient daprClient = mock(DaprClient.class);
        AdharDaprClient client = new AdharDaprClient(daprClient, previewClient);

        client.close();

        verify(daprClient).close();
        verify(previewClient).close();
    }
}
