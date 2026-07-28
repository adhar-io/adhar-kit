package com.adhar.kit.dapr;

import com.adhar.kit.dapr.api.ConfigurationCallback;
import com.adhar.kit.dapr.api.StateOperation;
import com.adhar.kit.dapr.api.StateWithETag;
import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dapr.client.domain.HttpExtension;
import io.dapr.client.domain.InvokeBindingRequest;
import io.dapr.client.domain.State;
import io.dapr.client.domain.SubscribeConfigurationResponse;
import io.dapr.client.domain.TransactionalStateOperation;
import io.dapr.utils.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprFacade} using a mocked {@link DaprClient}.
 */
class DaprFacadeTest {

    private DaprClient client;
    private DaprFacade facade;

    @BeforeEach
    void setUp() {
        client = mock(DaprClient.class);
        facade = new DaprFacade(client);
    }

    // ==================== State Management ====================

    @Test
    void saveStateDelegatesToClient() {
        when(client.saveState(anyString(), anyString(), any())).thenReturn(Mono.empty());

        facade.saveState("store", "key", "value");

        verify(client).saveState("store", "key", "value");
    }

    @Test
    void saveStateWrapsExceptionInDaprException() {
        when(client.saveState(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.saveState("store", "key", "value"))
            .isInstanceOf(DaprFacade.DaprException.class)
            .hasMessage("Failed to save state");
    }

    @Test
    void saveStateWithETagReturnsTrueOnSuccess() {
        when(client.saveState(anyString(), anyString(), any(), any(), any())).thenReturn(Mono.empty());

        boolean saved = facade.saveStateWithETag("store", "key", "value", "etag");

        assertThat(saved).isTrue();
    }

    @Test
    void saveStateWithETagReturnsFalseOnEtagMismatch() {
        when(client.saveState(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("ETag mismatch detected"));

        boolean saved = facade.saveStateWithETag("store", "key", "value", "etag");

        assertThat(saved).isFalse();
    }

    @Test
    void saveStateWithETagThrowsOnOtherFailure() {
        when(client.saveState(anyString(), anyString(), any(), any(), any()))
            .thenThrow(new RuntimeException("network down"));

        assertThatThrownBy(() -> facade.saveStateWithETag("store", "key", "value", "etag"))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void saveStateWithTtlDelegatesToClient() {
        when(client.saveState(anyString(), anyString(), any())).thenReturn(Mono.empty());

        facade.saveStateWithTTL("store", "key", "value", Duration.ofSeconds(30));

        verify(client).saveState("store", "key", "value");
    }

    @Test
    void saveStateWithTtlWrapsException() {
        when(client.saveState(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.saveStateWithTTL("store", "key", "value", Duration.ofSeconds(1)))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void getStateReturnsValueWhenPresent() {
        when(client.getState("store", "key", String.class))
            .thenReturn(Mono.just(new State<>("key", "value", "etag")));

        String result = facade.getState("store", "key", String.class);

        assertThat(result).isEqualTo("value");
    }

    @Test
    void getStateReturnsNullWhenStateAbsent() {
        when(client.getState("store", "key", String.class)).thenReturn(Mono.empty());

        assertThat(facade.getState("store", "key", String.class)).isNull();
    }

    @Test
    void getStateWrapsException() {
        when(client.getState("store", "key", String.class))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.getState("store", "key", String.class))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void getStateWithETagReturnsValueAndEtag() {
        when(client.getState("store", "key", String.class))
            .thenReturn(Mono.just(new State<>("key", "value", "etag-1")));

        StateWithETag<String> result = facade.getStateWithETag("store", "key", String.class);

        assertThat(result.getValue()).isEqualTo("value");
        assertThat(result.getEtag()).isEqualTo("etag-1");
    }

    @Test
    void getStateWithETagReturnsEmptyWhenStateNull() {
        when(client.getState("store", "key", String.class)).thenReturn(Mono.empty());

        StateWithETag<String> result = facade.getStateWithETag("store", "key", String.class);

        assertThat(result.getValue()).isNull();
        assertThat(result.getEtag()).isNull();
    }

    @Test
    void getStateWithETagWrapsException() {
        when(client.getState("store", "key", String.class))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.getStateWithETag("store", "key", String.class))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void getBulkStateReturnsMapFilteringNulls() {
        List<State<String>> states = List.of(
            new State<>("k1", "v1", "e1"),
            new State<String>("k2", null, "e2"));
        when(client.getBulkState(eq("store"), anyList(), eq(String.class)))
            .thenReturn(Mono.just(states));

        Map<String, String> result = facade.getBulkState("store", List.of("k1", "k2"), String.class);

        assertThat(result).containsEntry("k1", "v1").doesNotContainKey("k2");
    }

    @Test
    void getBulkStateReturnsEmptyMapWhenNull() {
        when(client.getBulkState(eq("store"), anyList(), eq(String.class)))
            .thenReturn(Mono.empty());

        assertThat(facade.getBulkState("store", List.of("k1"), String.class)).isEmpty();
    }

    @Test
    void getBulkStateWrapsException() {
        when(client.getBulkState(eq("store"), anyList(), eq(String.class)))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.getBulkState("store", List.of("k1"), String.class))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void deleteStateDelegatesToClient() {
        when(client.deleteState("store", "key")).thenReturn(Mono.empty());

        facade.deleteState("store", "key");

        verify(client).deleteState("store", "key");
    }

    @Test
    void deleteStateWrapsException() {
        when(client.deleteState("store", "key")).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.deleteState("store", "key"))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void deleteStateWithETagReturnsTrueOnSuccess() {
        when(client.deleteState(anyString(), anyString(), any(), any())).thenReturn(Mono.empty());

        assertThat(facade.deleteStateWithETag("store", "key", "etag")).isTrue();
    }

    @Test
    void deleteStateWithETagReturnsFalseOnEtagMismatch() {
        when(client.deleteState(anyString(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("ETag mismatch happened"));

        assertThat(facade.deleteStateWithETag("store", "key", "etag")).isFalse();
    }

    @Test
    void deleteStateWithETagThrowsOnOtherFailure() {
        when(client.deleteState(anyString(), anyString(), any(), any()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.deleteStateWithETag("store", "key", "etag"))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void executeStateTransactionMapsUpsertAndDeleteOperations() {
        when(client.executeStateTransaction(anyString(), anyList())).thenReturn(Mono.empty());

        facade.executeStateTransaction("store", List.of(
            StateOperation.upsert("k1", "v1", "e1"),
            StateOperation.delete("k2")));

        verify(client).executeStateTransaction(eq("store"),
            (List<TransactionalStateOperation<?>>) any());
    }

    @Test
    void executeStateTransactionWrapsException() {
        when(client.executeStateTransaction(anyString(), anyList()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.executeStateTransaction("store",
            List.of(StateOperation.delete("k"))))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void queryStateThrowsUnsupported() {
        assertThatThrownBy(() -> facade.queryState("store", "{}", String.class))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Pub/Sub ====================

    @Test
    void publishEventDelegatesWithEmptyMetadata() {
        when(client.publishEvent(anyString(), anyString(), any(), anyMap())).thenReturn(Mono.empty());

        facade.publishEvent("pubsub", "topic", "event");

        verify(client).publishEvent(eq("pubsub"), eq("topic"), eq("event"), anyMap());
    }

    @Test
    void publishEventWithMetadataWrapsException() {
        when(client.publishEvent(anyString(), anyString(), any(), anyMap()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.publishEvent("pubsub", "topic", "event", Map.of("k", "v")))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void publishBulkEventsPublishesEach() {
        when(client.publishEvent(anyString(), anyString(), any(), anyMap())).thenReturn(Mono.empty());

        facade.publishBulkEvents("pubsub", "topic", List.of("e1", "e2", "e3"));

        verify(client, times(3)).publishEvent(eq("pubsub"), eq("topic"), any(), anyMap());
    }

    @Test
    void publishBulkEventsWrapsException() {
        when(client.publishEvent(anyString(), anyString(), any(), anyMap()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.publishBulkEvents("pubsub", "topic", List.of("e1")))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    // ==================== Service Invocation ====================

    @Test
    void invokeServiceDefaultsToPost() {
        when(client.invokeMethod(eq("app"), eq("method"), any(), any(HttpExtension.class), eq(String.class)))
            .thenReturn(Mono.just("response"));

        String result = facade.invokeService("app", "method", "request", String.class);

        assertThat(result).isEqualTo("response");
    }

    @Test
    void invokeServiceSupportsAllHttpMethods() {
        when(client.invokeMethod(anyString(), anyString(), any(), any(HttpExtension.class), eq(String.class)))
            .thenReturn(Mono.just("ok"));

        assertThat(facade.invokeService("app", "m", "GET", "req", String.class)).isEqualTo("ok");
        assertThat(facade.invokeService("app", "m", "PUT", "req", String.class)).isEqualTo("ok");
        assertThat(facade.invokeService("app", "m", "DELETE", "req", String.class)).isEqualTo("ok");
        assertThat(facade.invokeService("app", "m", "PATCH", "req", String.class)).isEqualTo("ok");
    }

    @Test
    void invokeServiceWrapsException() {
        when(client.invokeMethod(anyString(), anyString(), any(), any(HttpExtension.class), eq(String.class)))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.invokeService("app", "method", "request", String.class))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void invokeServiceAsyncReturnsCompletedFuture() {
        when(client.invokeMethod(anyString(), anyString(), any(), any(HttpExtension.class), eq(String.class)))
            .thenReturn(Mono.just("async-response"));

        assertThat(facade.invokeServiceAsync("app", "method", "request", String.class).join())
            .isEqualTo("async-response");
    }

    // ==================== Bindings ====================

    @Test
    void invokeBindingDelegatesWithEmptyMetadata() {
        when(client.invokeBinding(any(InvokeBindingRequest.class), any(TypeRef.class)))
            .thenReturn(Mono.just(new byte[0]));

        facade.invokeBinding("binding", "create", "data");

        verify(client).invokeBinding(any(InvokeBindingRequest.class), any(TypeRef.class));
    }

    @Test
    void invokeBindingWithMetadataWrapsException() {
        when(client.invokeBinding(any(InvokeBindingRequest.class), any(TypeRef.class)))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.invokeBinding("binding", "create", "data", Map.of("k", "v")))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    // ==================== Secrets ====================

    @Test
    void getSecretReturnsValue() {
        when(client.getSecret(anyString(), anyString(), anyMap()))
            .thenReturn(Mono.just(Map.of("password", "s3cr3t")));

        assertThat(facade.getSecret("store", "password")).isEqualTo("s3cr3t");
    }

    @Test
    void getSecretReturnsNullWhenSecretsNull() {
        when(client.getSecret(anyString(), anyString(), anyMap())).thenReturn(Mono.empty());

        assertThat(facade.getSecret("store", "password")).isNull();
    }

    @Test
    void getSecretWrapsException() {
        when(client.getSecret(anyString(), anyString(), anyMap()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.getSecret("store", "password"))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void getBulkSecretsFlattensNestedMap() {
        when(client.getBulkSecret("store")).thenReturn(Mono.just(Map.of(
            "db", Map.of("password", "p"),
            "empty", Map.of())));

        Map<String, String> result = facade.getBulkSecrets("store");

        assertThat(result).containsEntry("db", "p").doesNotContainKey("empty");
    }

    @Test
    void getBulkSecretsReturnsEmptyWhenNull() {
        when(client.getBulkSecret("store")).thenReturn(Mono.empty());

        assertThat(facade.getBulkSecrets("store")).isEmpty();
    }

    @Test
    void getBulkSecretsWrapsException() {
        when(client.getBulkSecret("store")).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.getBulkSecrets("store"))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    // ==================== Configuration ====================

    @Test
    void getConfigurationSingleKeyReturnsValue() {
        when(client.getConfiguration(eq("store"), any(String[].class)))
            .thenReturn(Mono.just(Map.of("key", new ConfigurationItem("key", "val", "v1"))));

        assertThat(facade.getConfiguration("store", "key")).isEqualTo("val");
    }

    @Test
    void getConfigurationListReturnsEmptyWhenNull() {
        when(client.getConfiguration(eq("store"), any(String[].class))).thenReturn(Mono.empty());

        assertThat(facade.getConfiguration("store", List.of("key"))).isEmpty();
    }

    @Test
    void getConfigurationListWrapsException() {
        when(client.getConfiguration(eq("store"), any(String[].class)))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.getConfiguration("store", List.of("key")))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    @Test
    void subscribeConfigurationInvokesCallbackWithChanges() {
        SubscribeConfigurationResponse response = new SubscribeConfigurationResponse(
            "id", Map.of("flag", new ConfigurationItem("flag", "on", "v1")));
        when(client.subscribeConfiguration(eq("store"), any(String[].class)))
            .thenReturn(Flux.just(response));

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        ConfigurationCallback callback = captured::set;

        facade.subscribeConfiguration("store", List.of("flag"), callback);

        assertThat(captured.get()).containsEntry("flag", "on");
    }

    @Test
    void subscribeConfigurationIgnoresNullItems() {
        SubscribeConfigurationResponse response = mock(SubscribeConfigurationResponse.class);
        when(response.getItems()).thenReturn(null);
        when(client.subscribeConfiguration(eq("store"), any(String[].class)))
            .thenReturn(Flux.just(response));

        AtomicReference<Map<String, String>> captured = new AtomicReference<>();
        facade.subscribeConfiguration("store", List.of("flag"), captured::set);

        assertThat(captured.get()).isNull();
    }

    @Test
    void subscribeConfigurationWrapsException() {
        when(client.subscribeConfiguration(eq("store"), any(String[].class)))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> facade.subscribeConfiguration("store", List.of("flag"), c -> {}))
            .isInstanceOf(DaprFacade.DaprException.class);
    }

    // ==================== Unsupported operations ====================

    @Test
    void distributedLockRequiresPreviewClient() {
        // This facade was constructed without a preview client, so locking is unavailable.
        assertThatThrownBy(() -> facade.tryLock("store", "res", "owner", 30))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> facade.unlock("store", "res", "owner"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void actorOperationsAreUnsupported() {
        assertThatThrownBy(() -> facade.invokeActor("Type", "id", "m", null, String.class))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facade.saveActorState("Type", "id", "state", "v"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facade.getActorState("Type", "id", "state", String.class))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facade.registerActorReminder("Type", "id", "r",
            Duration.ZERO, Duration.ZERO, "data"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> facade.registerActorTimer("Type", "id", "t",
            Duration.ZERO, Duration.ZERO, "cb"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Utilities ====================

    @Test
    void isAvailableReturnsTrueForInjectedClient() {
        assertThat(facade.isAvailable()).isTrue();
    }

    @Test
    void getMetadataReturnsAvailabilityAndVersion() {
        Map<String, Object> metadata = facade.getMetadata();

        assertThat(metadata).containsEntry("available", true).containsEntry("version", "1.0.0");
    }

    @Test
    void shutdownClosesClient() throws Exception {
        facade.shutdown();

        verify(client).close();
    }

    @Test
    void shutdownSwallowsExceptions() throws Exception {
        doThrow(new RuntimeException("boom")).when(client).close();

        facade.shutdown();

        verify(client).close();
    }

    @Test
    void daprExceptionConstructors() {
        DaprFacade.DaprException withMessage = new DaprFacade.DaprException("oops");
        DaprFacade.DaprException withCause = new DaprFacade.DaprException("oops", new IllegalStateException());

        assertThat(withMessage).hasMessage("oops");
        assertThat(withCause).hasMessage("oops").hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void getInstanceReturnsSingleton() {
        DaprFacade first = DaprFacade.getInstance();
        DaprFacade second = DaprFacade.getInstance();

        assertThat(first).isNotNull().isSameAs(second);
    }

    @Test
    void noOpVerifyNeverPublishesOnEmptyBulk() {
        facade.publishBulkEvents("pubsub", "topic", List.of());

        verify(client, never()).publishEvent(anyString(), anyString(), any(), anyMap());
    }
}
