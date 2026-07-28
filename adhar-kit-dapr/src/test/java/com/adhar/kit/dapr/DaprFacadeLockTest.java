package com.adhar.kit.dapr;

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
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprFacade}'s distributed-lock methods, backed by a mocked
 * {@link DaprPreviewClient}.
 */
class DaprFacadeLockTest {

    private DaprPreviewClient previewClient;
    private DaprFacade facade;

    @BeforeEach
    void setUp() {
        previewClient = mock(DaprPreviewClient.class);
        facade = new DaprFacade(mock(DaprClient.class), previewClient);
    }

    @Test
    void tryLockReturnsTrueWhenAcquired() {
        when(previewClient.tryLock(any(LockRequest.class))).thenReturn(Mono.just(true));

        assertThat(facade.tryLock("lockstore", "res", "owner", 30)).isTrue();
    }

    @Test
    void tryLockReturnsFalseWhenNotAcquired() {
        when(previewClient.tryLock(any(LockRequest.class))).thenReturn(Mono.just(false));

        assertThat(facade.tryLock("lockstore", "res", "owner", 30)).isFalse();
    }

    @Test
    void tryLockReturnsFalseWhenNullResult() {
        when(previewClient.tryLock(any(LockRequest.class))).thenReturn(Mono.empty());

        assertThat(facade.tryLock("lockstore", "res", "owner", 30)).isFalse();
    }

    @Test
    void tryLockWrapsException() {
        when(previewClient.tryLock(any(LockRequest.class))).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.tryLock("lockstore", "res", "owner", 30))
            .isInstanceOf(DaprFacade.DaprException.class)
            .hasMessage("Failed to acquire lock");
    }

    @Test
    void unlockReturnsTrueOnSuccess() {
        when(previewClient.unlock(any(UnlockRequest.class)))
            .thenReturn(Mono.just(UnlockResponseStatus.SUCCESS));

        assertThat(facade.unlock("lockstore", "res", "owner")).isTrue();
    }

    @Test
    void unlockReturnsFalseWhenLockBelongsToOthers() {
        when(previewClient.unlock(any(UnlockRequest.class)))
            .thenReturn(Mono.just(UnlockResponseStatus.LOCK_BELONG_TO_OTHERS));

        assertThat(facade.unlock("lockstore", "res", "owner")).isFalse();
    }

    @Test
    void unlockWrapsException() {
        when(previewClient.unlock(any(UnlockRequest.class))).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.unlock("lockstore", "res", "owner"))
            .isInstanceOf(DaprFacade.DaprException.class)
            .hasMessage("Failed to release lock");
    }

    @Test
    void tryLockWithoutPreviewClientThrowsIllegalState() {
        DaprFacade noPreview = new DaprFacade(mock(DaprClient.class));

        assertThatThrownBy(() -> noPreview.tryLock("lockstore", "res", "owner", 30))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DaprPreviewClient");
    }

    @Test
    void unlockWithoutPreviewClientThrowsIllegalState() {
        DaprFacade noPreview = new DaprFacade(mock(DaprClient.class));

        assertThatThrownBy(() -> noPreview.unlock("lockstore", "res", "owner"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("DaprPreviewClient");
    }

    @Test
    void shutdownClosesPreviewClient() throws Exception {
        facade.shutdown();

        org.mockito.Mockito.verify(previewClient).close();
    }
}
