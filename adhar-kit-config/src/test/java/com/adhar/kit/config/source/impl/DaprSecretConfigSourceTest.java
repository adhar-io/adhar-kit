package com.adhar.kit.config.source.impl;

import com.adhar.kit.dapr.DaprFacade;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprSecretConfigSource} with a mocked {@link DaprFacade}.
 */
class DaprSecretConfigSourceTest {

    private final DaprFacade daprFacade = mock(DaprFacade.class);

    @Test
    void bulkLoadsSecretsAtConstruction() {
        when(daprFacade.getBulkSecrets("secretstore"))
                .thenReturn(Map.of("db-password", "s3cret", "api.key", "k"));

        DaprSecretConfigSource source = new DaprSecretConfigSource(daprFacade, "secretstore", 160);

        assertThat(source.loadConfig())
                .containsEntry("db-password", "s3cret")
                .containsEntry("api.key", "k");
        assertThat(source.isHealthy()).isTrue();
        assertThat(source.getType()).isEqualTo("dapr-secrets");
        assertThat(source.getPriority()).isEqualTo(160);
    }

    @Test
    void bulkFailureStillAllowsPerKeyLookups() {
        when(daprFacade.getBulkSecrets("secretstore"))
                .thenThrow(new RuntimeException("bulk not permitted"));
        when(daprFacade.getSecret("secretstore", "db-password")).thenReturn("s3cret");

        DaprSecretConfigSource source = new DaprSecretConfigSource(daprFacade, "secretstore", 160);

        assertThat(source.isHealthy()).isFalse();
        assertThat(source.getProperty("db-password")).contains("s3cret");
        // Second lookup served from cache.
        assertThat(source.getProperty("db-password")).contains("s3cret");
        verify(daprFacade).getSecret("secretstore", "db-password");
    }

    @Test
    void perKeyFailureIsAMissNotAnException() {
        when(daprFacade.getBulkSecrets("secretstore")).thenReturn(Map.of());
        when(daprFacade.getSecret("secretstore", "missing"))
                .thenThrow(new RuntimeException("sidecar down"));

        DaprSecretConfigSource source = new DaprSecretConfigSource(daprFacade, "secretstore", 160);

        assertThat(source.getProperty("missing")).isEmpty();
    }

    @Test
    void refreshReloadsSecrets() {
        when(daprFacade.getBulkSecrets("secretstore"))
                .thenReturn(Map.of("k", "v1"))
                .thenReturn(Map.of("k", "v2"));

        DaprSecretConfigSource source = new DaprSecretConfigSource(daprFacade, "secretstore", 160);

        assertThat(source.getProperty("k")).contains("v1");
        assertThat(source.refresh()).isTrue();
        assertThat(source.getProperty("k")).contains("v2");
    }
}
