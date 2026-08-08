package com.adhar.adharkit.security.dapr;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.security.dapr.DaprSecretKeyProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprSecretKeyProvider} with a mocked {@link DaprFacade}.
 */
class DaprSecretKeyProviderTest {

    private final DaprFacade daprFacade = mock(DaprFacade.class);

    @Test
    void resolvesAndCachesSecret() {
        when(daprFacade.getSecret("secretstore", "jwt-signing-key")).thenReturn("a-very-long-secret-key-material-1234");
        DaprSecretKeyProvider provider =
                new DaprSecretKeyProvider(daprFacade, "secretstore", "jwt-signing-key");

        assertThat(provider.resolve()).isEqualTo("a-very-long-secret-key-material-1234");
        assertThat(provider.resolve()).isEqualTo("a-very-long-secret-key-material-1234");
        verify(daprFacade, times(1)).getSecret("secretstore", "jwt-signing-key");
    }

    @Test
    void missingSecretFailsLoudly() {
        when(daprFacade.getSecret("secretstore", "jwt-signing-key")).thenReturn(null);
        DaprSecretKeyProvider provider =
                new DaprSecretKeyProvider(daprFacade, "secretstore", "jwt-signing-key");

        assertThatThrownBy(provider::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void sidecarFailureFailsLoudly() {
        when(daprFacade.getSecret("secretstore", "jwt-signing-key"))
                .thenThrow(new RuntimeException("sidecar down"));
        DaprSecretKeyProvider provider =
                new DaprSecretKeyProvider(daprFacade, "secretstore", "jwt-signing-key");

        assertThatThrownBy(provider::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to read");
    }

    @Test
    void nullArgumentsAreRejected() {
        assertThatThrownBy(() -> new DaprSecretKeyProvider(null, "s", "n"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DaprSecretKeyProvider(daprFacade, null, "n"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DaprSecretKeyProvider(daprFacade, "s", null))
                .isInstanceOf(NullPointerException.class);
    }
}
