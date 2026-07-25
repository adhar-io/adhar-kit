package com.adhar.kit.dapr.state;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.StateWithETag;
import io.dapr.client.DaprClient;
import io.dapr.client.domain.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StateRepository}: typed lookups, unconditional saves, and the
 * optimistic-concurrency retry loop backing {@link StateRepository#update}.
 */
@DisplayName("StateRepository Tests")
class StateRepositoryTest {

    private DaprClient client;
    private DaprFacade facade;
    private StateRepository<String> repository;

    @BeforeEach
    void setUp() {
        client = mock(DaprClient.class);
        facade = new DaprFacade(client);
        repository = new StateRepository<>(facade, "store", String.class);
    }

    @Test
    @DisplayName("find returns the value when present")
    void findReturnsValueWhenPresent() {
        when(client.getState("store", "k1", String.class))
                .thenReturn(Mono.just(new State<>("k1", "v1", "etag-1")));

        assertThat(repository.find("k1")).contains("v1");
    }

    @Test
    @DisplayName("find returns empty when absent")
    void findReturnsEmptyWhenAbsent() {
        when(client.getState("store", "k1", String.class)).thenReturn(Mono.empty());

        assertThat(repository.find("k1")).isEmpty();
    }

    @Test
    @DisplayName("findWithETag exposes both value and etag")
    void findWithETagExposesValueAndEtag() {
        when(client.getState("store", "k1", String.class))
                .thenReturn(Mono.just(new State<>("k1", "v1", "etag-1")));

        StateWithETag<String> result = repository.findWithETag("k1");

        assertThat(result.getValue()).isEqualTo("v1");
        assertThat(result.getEtag()).isEqualTo("etag-1");
    }

    @Test
    @DisplayName("save delegates to an unconditional save")
    void saveDelegatesToUnconditionalSave() {
        when(client.saveState(anyString(), anyString(), any())).thenReturn(Mono.empty());

        repository.save("k1", "v1");

        verify(client).saveState("store", "k1", "v1");
    }

    @Test
    @DisplayName("update applies the updater and saves on the first attempt when no conflict occurs")
    void updateSucceedsOnFirstAttempt() {
        when(client.getState("store", "k1", String.class))
                .thenReturn(Mono.just(new State<>("k1", "1", "etag-1")));
        when(client.saveState(anyString(), anyString(), any(), any(), any())).thenReturn(Mono.empty());

        String result = repository.update("k1", value -> value == null ? "1" : String.valueOf(Integer.parseInt(value) + 1));

        assertThat(result).isEqualTo("2");
        verify(client, times(1)).saveState(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("update retries on ETag conflict and eventually succeeds")
    void updateRetriesOnEtagConflictThenSucceeds() {
        when(client.getState("store", "k1", String.class))
                .thenReturn(Mono.just(new State<>("k1", "1", "etag-1")));
        AtomicInteger saveAttempts = new AtomicInteger();
        when(client.saveState(anyString(), anyString(), any(), any(), any())).thenAnswer(invocation -> {
            if (saveAttempts.incrementAndGet() < 3) {
                return Mono.error(new RuntimeException("ETag mismatch detected"));
            }
            return Mono.empty();
        });

        String result = repository.update("k1", value -> "updated");

        assertThat(result).isEqualTo("updated");
        assertThat(saveAttempts.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("update throws OptimisticConcurrencyException after exhausting all retries")
    void updateThrowsAfterExhaustingRetries() {
        StateRepository<String> limited = new StateRepository<>(facade, "store", String.class, 2);
        when(client.getState("store", "k1", String.class))
                .thenReturn(Mono.just(new State<>("k1", "1", "etag-1")));
        when(client.saveState(anyString(), anyString(), any(), any(), any()))
                .thenReturn(Mono.error(new RuntimeException("ETag mismatch detected")));

        assertThatThrownBy(() -> limited.update("k1", value -> "updated"))
                .isInstanceOf(OptimisticConcurrencyException.class)
                .hasMessageContaining("k1");
    }

    @Test
    @DisplayName("delete performs an ETag-guarded delete when the key exists")
    void deletePerformsEtagGuardedDeleteWhenPresent() {
        when(client.getState("store", "k1", String.class))
                .thenReturn(Mono.just(new State<>("k1", "v1", "etag-1")));
        when(client.deleteState(anyString(), anyString(), any(), any())).thenReturn(Mono.empty());

        assertThat(repository.delete("k1")).isTrue();
        verify(client).deleteState(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("delete returns false without calling delete when the key does not exist")
    void deleteReturnsFalseWhenKeyAbsent() {
        when(client.getState("store", "k1", String.class)).thenReturn(Mono.empty());

        assertThat(repository.delete("k1")).isFalse();
        verify(client, times(0)).deleteState(anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("constructor rejects a non-positive maxRetries")
    void constructorRejectsNonPositiveMaxRetries() {
        assertThatThrownBy(() -> new StateRepository<>(facade, "store", String.class, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("constructor rejects null arguments")
    void constructorRejectsNullArguments() {
        assertThatThrownBy(() -> new StateRepository<>(null, "store", String.class))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StateRepository<>(facade, null, String.class))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new StateRepository<>(facade, "store", null))
                .isInstanceOf(NullPointerException.class);
    }
}
