package com.adhar.kit.dapr.client;

import io.dapr.client.DaprClient;
import io.dapr.client.domain.ConfigurationItem;
import io.dapr.client.domain.HttpExtension;
import io.dapr.client.domain.State;
import io.dapr.utils.TypeRef;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdharDaprClient} using a mocked {@link DaprClient}.
 */
class AdharDaprClientTest {

    private DaprClient client;
    private AdharDaprClient dapr;

    @BeforeEach
    void setUp() {
        client = mock(DaprClient.class);
        dapr = new AdharDaprClient(client);
    }

    // ==================== State Management ====================

    @Test
    void saveStateDelegatesToClient() {
        when(client.saveState(anyString(), anyString(), any())).thenReturn(Mono.empty());

        dapr.saveState("store", "key", "value");

        verify(client).saveState("store", "key", "value");
    }

    @Test
    void saveStateWrapsException() {
        when(client.saveState(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.saveState("store", "key", "value"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to save state");
    }

    @Test
    void saveStateWithMetadataDelegatesToClient() {
        when(client.saveState(anyString(), anyString(), any())).thenReturn(Mono.empty());

        dapr.saveState("store", "key", "value", Map.of("ttl", "60"));

        verify(client).saveState("store", "key", "value");
    }

    @Test
    void saveStateWithMetadataWrapsException() {
        when(client.saveState(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.saveState("store", "key", "value", Map.of("ttl", "60")))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void getStateReturnsValueWhenPresent() {
        when(client.getState("store", "key", String.class))
            .thenReturn(Mono.just(new State<>("key", "value", "etag")));

        Optional<String> result = dapr.getState("store", "key", String.class);

        assertThat(result).contains("value");
    }

    @Test
    void getStateReturnsEmptyWhenStateNull() {
        when(client.getState("store", "key", String.class)).thenReturn(Mono.empty());

        assertThat(dapr.getState("store", "key", String.class)).isEmpty();
    }

    @Test
    void getStateReturnsEmptyWhenValueNull() {
        when(client.getState("store", "key", String.class))
            .thenReturn(Mono.just(new State<String>("key", null, "etag")));

        assertThat(dapr.getState("store", "key", String.class)).isEmpty();
    }

    @Test
    void getStateReturnsEmptyOnException() {
        when(client.getState("store", "key", String.class))
            .thenThrow(new RuntimeException("boom"));

        assertThat(dapr.getState("store", "key", String.class)).isEmpty();
    }

    @Test
    void deleteStateDelegatesToClient() {
        when(client.deleteState("store", "key")).thenReturn(Mono.empty());

        dapr.deleteState("store", "key");

        verify(client).deleteState("store", "key");
    }

    @Test
    void deleteStateWrapsException() {
        when(client.deleteState("store", "key")).thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.deleteState("store", "key"))
            .isInstanceOf(RuntimeException.class);
    }

    // ==================== Pub/Sub ====================

    @Test
    void publishEventDelegatesToClient() {
        when(client.publishEvent(anyString(), anyString(), any())).thenReturn(Mono.empty());

        dapr.publishEvent("pubsub", "topic", "event");

        verify(client).publishEvent("pubsub", "topic", "event");
    }

    @Test
    void publishEventWrapsException() {
        when(client.publishEvent(anyString(), anyString(), any()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.publishEvent("pubsub", "topic", "event"))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void publishEventWithMetadataDelegatesToClient() {
        when(client.publishEvent(anyString(), anyString(), any(), anyMap())).thenReturn(Mono.empty());

        dapr.publishEvent("pubsub", "topic", "event", Map.of("k", "v"));

        verify(client).publishEvent(eq("pubsub"), eq("topic"), eq("event"), anyMap());
    }

    @Test
    void publishEventWithMetadataWrapsException() {
        when(client.publishEvent(anyString(), anyString(), any(), anyMap()))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.publishEvent("pubsub", "topic", "event", Map.of("k", "v")))
            .isInstanceOf(RuntimeException.class);
    }

    // ==================== Service Invocation ====================

    @Test
    void invokeServiceReturnsResponse() {
        when(client.invokeMethod(eq("app"), eq("/endpoint"), any(), any(HttpExtension.class), eq(String.class)))
            .thenReturn(Mono.just("response"));

        String result = dapr.invokeService("app", "POST", "/endpoint", "request", String.class);

        assertThat(result).isEqualTo("response");
    }

    @Test
    void invokeServiceSupportsAllHttpMethods() {
        when(client.invokeMethod(anyString(), anyString(), any(), any(HttpExtension.class), eq(String.class)))
            .thenReturn(Mono.just("ok"));

        assertThat(dapr.invokeService("app", "GET", "/e", null, String.class)).isEqualTo("ok");
        assertThat(dapr.invokeService("app", "PUT", "/e", "r", String.class)).isEqualTo("ok");
        assertThat(dapr.invokeService("app", "DELETE", "/e", "r", String.class)).isEqualTo("ok");
        assertThat(dapr.invokeService("app", "PATCH", "/e", "r", String.class)).isEqualTo("ok");
    }

    @Test
    void invokeServiceWrapsException() {
        when(client.invokeMethod(anyString(), anyString(), any(), any(HttpExtension.class), eq(String.class)))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.invokeService("app", "POST", "/e", "r", String.class))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to invoke service");
    }

    @Test
    void getDelegatesToInvokeServiceWithGet() {
        when(client.invokeMethod(eq("app"), eq("/e"), any(), any(HttpExtension.class), eq(String.class)))
            .thenReturn(Mono.just("got"));

        assertThat(dapr.get("app", "/e", String.class)).isEqualTo("got");
    }

    @Test
    void postDelegatesToInvokeServiceWithPost() {
        when(client.invokeMethod(eq("app"), eq("/e"), any(), any(HttpExtension.class), eq(String.class)))
            .thenReturn(Mono.just("posted"));

        assertThat(dapr.post("app", "/e", "body", String.class)).isEqualTo("posted");
    }

    // ==================== Secrets ====================

    @Test
    void getSecretReturnsValue() {
        when(client.getSecret("store", "name")).thenReturn(Mono.just(Map.of("name", "value")));

        assertThat(dapr.getSecret("store", "name")).contains("value");
    }

    @Test
    void getSecretReturnsEmptyWhenSecretsEmpty() {
        when(client.getSecret("store", "name")).thenReturn(Mono.just(Map.of()));

        assertThat(dapr.getSecret("store", "name")).isEmpty();
    }

    @Test
    void getSecretReturnsEmptyWhenSecretsNull() {
        when(client.getSecret("store", "name")).thenReturn(Mono.empty());

        assertThat(dapr.getSecret("store", "name")).isEmpty();
    }

    @Test
    void getSecretReturnsEmptyOnException() {
        when(client.getSecret("store", "name")).thenThrow(new RuntimeException("boom"));

        assertThat(dapr.getSecret("store", "name")).isEmpty();
    }

    @Test
    void getAllSecretsReturnsMap() {
        Map<String, String> secrets = Map.of("a", "1", "b", "2");
        when(client.getSecret("store", "name")).thenReturn(Mono.just(secrets));

        assertThat(dapr.getAllSecrets("store", "name")).isEqualTo(secrets);
    }

    @Test
    void getAllSecretsReturnsEmptyMapOnException() {
        when(client.getSecret("store", "name")).thenThrow(new RuntimeException("boom"));

        assertThat(dapr.getAllSecrets("store", "name")).isEmpty();
    }

    // ==================== Bindings ====================

    @Test
    void invokeBindingDelegatesToClient() {
        when(client.invokeBinding("binding", "create", "data")).thenReturn(Mono.empty());

        dapr.invokeBinding("binding", "create", "data");

        verify(client).invokeBinding("binding", "create", "data");
    }

    @Test
    void invokeBindingWrapsException() {
        when(client.invokeBinding("binding", "create", "data"))
            .thenThrow(new RuntimeException("boom"));

        assertThatThrownBy(() -> dapr.invokeBinding("binding", "create", "data"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Failed to invoke binding");
    }

    // ==================== Configuration ====================

    @Test
    void getConfigurationReturnsValue() {
        when(client.getConfiguration(eq("store"), any(String[].class)))
            .thenReturn(Mono.just(Map.of("key", new ConfigurationItem("key", "val", "v1"))));

        assertThat(dapr.getConfiguration("store", "key")).contains("val");
    }

    @Test
    void getConfigurationReturnsEmptyWhenKeyAbsent() {
        when(client.getConfiguration(eq("store"), any(String[].class)))
            .thenReturn(Mono.just(Map.of("other", new ConfigurationItem("other", "x", "v1"))));

        assertThat(dapr.getConfiguration("store", "key")).isEmpty();
    }

    @Test
    void getConfigurationReturnsEmptyWhenConfigNull() {
        when(client.getConfiguration(eq("store"), any(String[].class))).thenReturn(Mono.empty());

        assertThat(dapr.getConfiguration("store", "key")).isEmpty();
    }

    @Test
    void getConfigurationReturnsEmptyOnException() {
        when(client.getConfiguration(eq("store"), any(String[].class)))
            .thenThrow(new RuntimeException("boom"));

        assertThat(dapr.getConfiguration("store", "key")).isEmpty();
    }

    @Test
    void getConfigurationsReturnsFilteredMap() {
        when(client.getConfiguration(eq("store"), any(String[].class)))
            .thenReturn(Mono.just(Map.of(
                "a", new ConfigurationItem("a", "1", "v1"),
                "b", new ConfigurationItem("b", "2", "v1"))));

        Map<String, String> result = dapr.getConfigurations("store", "a", "b");

        assertThat(result).containsEntry("a", "1").containsEntry("b", "2");
    }

    @Test
    void getConfigurationsReturnsEmptyWhenConfigNull() {
        when(client.getConfiguration(eq("store"), any(String[].class))).thenReturn(Mono.empty());

        assertThat(dapr.getConfigurations("store", "a")).isEmpty();
    }

    @Test
    void getConfigurationsReturnsEmptyMapOnException() {
        when(client.getConfiguration(eq("store"), any(String[].class)))
            .thenThrow(new RuntimeException("boom"));

        assertThat(dapr.getConfigurations("store", "a")).isEmpty();
    }

    // ==================== Unsupported operations ====================

    @Test
    void distributedLockRequiresPreviewClient() {
        // This client was constructed without a preview client, so locking is unavailable.
        assertThatThrownBy(() -> dapr.tryLock("store", "res", "owner", 30))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> dapr.unlock("store", "res", "owner"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cryptoOperationsAreUnsupported() {
        assertThatThrownBy(() -> dapr.encrypt("crypto", new byte[]{1}, "AES"))
            .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> dapr.decrypt("crypto", new byte[]{1}, "AES"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    // ==================== Lifecycle ====================

    @Test
    void closeClosesUnderlyingClient() throws Exception {
        dapr.close();

        verify(client).close();
    }

    @Test
    void closeSwallowsExceptions() throws Exception {
        doThrow(new RuntimeException("boom")).when(client).close();

        dapr.close();

        verify(client).close();
    }
}
