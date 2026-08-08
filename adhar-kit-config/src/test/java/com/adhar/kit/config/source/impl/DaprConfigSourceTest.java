package com.adhar.kit.config.source.impl;

import com.adhar.kit.dapr.DaprFacade;
import com.adhar.kit.dapr.api.ConfigurationCallback;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DaprConfigSource} with a mocked {@link DaprFacade}.
 */
class DaprConfigSourceTest {

    private final DaprFacade daprFacade = mock(DaprFacade.class);

    @Test
    void loadsConfiguredKeysAtConstruction() {
        when(daprFacade.getConfiguration("configstore", List.of("app.timeout")))
                .thenReturn(Map.of("app.timeout", "30"));

        DaprConfigSource source = new DaprConfigSource(
                daprFacade, "configstore", List.of("app.timeout"), 150, false);

        assertThat(source.loadConfig()).containsEntry("app.timeout", "30");
        assertThat(source.getProperty("app.timeout")).contains("30");
        assertThat(source.isHealthy()).isTrue();
        assertThat(source.getType()).isEqualTo("dapr");
        assertThat(source.getPriority()).isEqualTo(150);
        verify(daprFacade, never()).subscribeConfiguration(anyString(), anyList(), any());
    }

    @Test
    void cacheMissFallsThroughToDirectRead() {
        when(daprFacade.getConfiguration(eq("configstore"), anyList())).thenReturn(Map.of());
        when(daprFacade.getConfiguration("configstore", "ad-hoc")).thenReturn("value");

        DaprConfigSource source = new DaprConfigSource(
                daprFacade, "configstore", List.of(), 150, false);

        assertThat(source.getProperty("ad-hoc")).contains("value");
        // Second read must come from cache.
        assertThat(source.getProperty("ad-hoc")).contains("value");
        verify(daprFacade).getConfiguration("configstore", "ad-hoc");
    }

    @Test
    void sidecarFailureIsAMissNotAnException() {
        when(daprFacade.getConfiguration(eq("configstore"), anyList()))
                .thenThrow(new RuntimeException("sidecar down"));
        when(daprFacade.getConfiguration(eq("configstore"), anyString()))
                .thenThrow(new RuntimeException("sidecar down"));

        DaprConfigSource source = new DaprConfigSource(
                daprFacade, "configstore", List.of("k"), 150, false);

        assertThat(source.isHealthy()).isFalse();
        assertThat(source.getProperty("k")).isEmpty();
        assertThat(source.loadConfig()).isEmpty();
    }

    @Test
    void subscriptionUpdatesCache() {
        when(daprFacade.getConfiguration("configstore", List.of("feature.x")))
                .thenReturn(Map.of("feature.x", "off"));

        DaprConfigSource source = new DaprConfigSource(
                daprFacade, "configstore", List.of("feature.x"), 150, true);

        ArgumentCaptor<ConfigurationCallback> callback =
                ArgumentCaptor.forClass(ConfigurationCallback.class);
        verify(daprFacade).subscribeConfiguration(eq("configstore"), eq(List.of("feature.x")),
                callback.capture());

        callback.getValue().onConfigurationChange(Map.of("feature.x", "on"));

        assertThat(source.getProperty("feature.x")).contains("on");
    }

    @Test
    void refreshReloadsFromStore() {
        when(daprFacade.getConfiguration("configstore", List.of("k")))
                .thenReturn(Map.of("k", "v1"))
                .thenReturn(Map.of("k", "v2"));

        DaprConfigSource source = new DaprConfigSource(
                daprFacade, "configstore", List.of("k"), 150, false);

        assertThat(source.getProperty("k")).contains("v1");
        assertThat(source.refresh()).isTrue();
        assertThat(source.getProperty("k")).contains("v2");
        assertThat(source.supportsRefresh()).isTrue();
    }
}
