package com.adhar.kit.dapr.client;

import com.adhar.kit.dapr.DaprError;
import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.state.SaveOptions;
import com.adhar.kit.dapr.support.MemoryDapr;
import io.dapr.client.DaprClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AdharDaprClient} is a view of {@link DaprFacade}: it delegates, shares the facade's client
 * and lets failures propagate instead of turning them into empty results.
 */
@SuppressWarnings("deprecation")
class AdharDaprClientTest {

    @Test
    void stateRoundTripsThroughTheFacade() {
        DaprFacade facade = MemoryDapr.facade();
        AdharDaprClient client = new AdharDaprClient(facade, null);

        client.saveState("statestore", "k", "v");
        client.saveState("statestore", "ttl", "v", Map.of("ttlInSeconds", "60"));

        assertThat(client.getState("statestore", "k", String.class)).contains("v");
        assertThat(client.getState("statestore", "missing", String.class)).isEmpty();
        client.deleteState("statestore", "k");
        assertThat(client.getState("statestore", "k", String.class)).isEmpty();
        assertThat(client.facade()).isSameAs(facade);
    }

    @Test
    void failuresPropagateInsteadOfLookingLikeMissingValues() {
        DaprFacade facade = mock(DaprFacade.class);
        DaprError down = new DaprError("Failed to get state: connection refused", 0, "UNAVAILABLE", "refused");
        when(facade.getState("statestore", "k", String.class)).thenThrow(down);
        when(facade.getSecret("secretstore", "db")).thenThrow(down);
        when(facade.getConfiguration("configstore", "flag")).thenThrow(down);
        AdharDaprClient client = new AdharDaprClient(facade, null);

        assertThatThrownBy(() -> client.getState("statestore", "k", String.class)).isSameAs(down);
        assertThatThrownBy(() -> client.getSecret("secretstore", "db")).isSameAs(down);
        assertThatThrownBy(() -> client.getConfiguration("configstore", "flag")).isSameAs(down);
    }

    @Test
    void everyOperationDelegates() {
        DaprFacade facade = mock(DaprFacade.class);
        AdharDaprClient client = new AdharDaprClient(facade, "key-1");
        when(facade.invokeService("orders", "/orders/1", "PATCH", Map.of("a", 1), String.class)).thenReturn("patched");
        when(facade.invokeService("orders", "/orders/1", "GET", null, String.class)).thenReturn("got");
        when(facade.invokeService("orders", "/orders", "POST", "body", String.class)).thenReturn("posted");
        when(facade.getSecret("secretstore", "db")).thenReturn("pw");
        when(facade.getSecretValues("secretstore", "db")).thenReturn(Map.of("password", "pw"));
        when(facade.getConfiguration("configstore", "flag")).thenReturn("on");
        when(facade.getConfiguration("configstore", List.of("a", "b"))).thenReturn(Map.of("a", "1"));
        when(facade.tryLock("lockstore", "r", "o", 5)).thenReturn(true);
        when(facade.unlock("lockstore", "r", "o")).thenReturn(true);
        when(facade.encrypt("crypto", "key-1", "A256KW", new byte[] {1})).thenReturn(new byte[] {9});
        when(facade.decrypt("crypto", new byte[] {9}, "key-1")).thenReturn(new byte[] {1});

        client.publishEvent("pubsub", "orders", "e");
        client.publishEvent("pubsub", "orders", "e", Map.of("k", "v"));
        client.invokeBinding("email", "create", "x");
        assertThat(client.invokeService("orders", "PATCH", "/orders/1", Map.of("a", 1), String.class)).isEqualTo("patched");
        assertThat(client.get("orders", "/orders/1", String.class)).isEqualTo("got");
        assertThat(client.post("orders", "/orders", "body", String.class)).isEqualTo("posted");
        assertThat(client.getSecret("secretstore", "db")).isEqualTo(Optional.of("pw"));
        assertThat(client.getAllSecrets("secretstore", "db")).containsEntry("password", "pw");
        assertThat(client.getConfiguration("configstore", "flag")).contains("on");
        assertThat(client.getConfigurations("configstore", "a", "b")).containsEntry("a", "1");
        assertThat(client.tryLock("lockstore", "r", "o", 5)).isTrue();
        assertThat(client.unlock("lockstore", "r", "o")).isTrue();
        assertThat(client.encrypt("crypto", new byte[] {1}, "A256KW")).containsExactly(9);
        assertThat(client.decrypt("crypto", new byte[] {9}, "A256KW")).containsExactly(1);

        verify(facade).publishEvent("pubsub", "orders", "e");
        verify(facade).publishEvent("pubsub", "orders", "e", Map.of("k", "v"));
        verify(facade).invokeBinding("email", "create", "x");
    }

    @Test
    void saveStateMetadataBecomesSaveOptions() {
        DaprFacade facade = mock(DaprFacade.class);
        new AdharDaprClient(facade, null).saveState("statestore", "k", "v", Map.of("ttlInSeconds", "60"));
        verify(facade).saveState(eq("statestore"), eq("k"), eq("v"), any(SaveOptions.class));
    }

    @Test
    void cryptoWithoutAConfiguredKeyExplainsWhatToSet() {
        AdharDaprClient client = new AdharDaprClient(mock(DaprFacade.class), null);
        assertThatThrownBy(() -> client.encrypt("crypto", new byte[] {1}, "A256KW"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("adhar.dapr.crypto.default-key");
    }

    @Test
    void closesOnlyAFacadeItCreated() throws Exception {
        DaprClient sdk = mock(DaprClient.class);
        new AdharDaprClient(sdk).close();
        DaprFacade shared = mock(DaprFacade.class);
        new AdharDaprClient(shared, null).close();
        org.mockito.Mockito.verify(shared, org.mockito.Mockito.never()).close();
    }
}
